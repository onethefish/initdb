package cn.fish.initDB.event.listen;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.common.ai.ChatModelUsageRecorder;
import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.WorkflowConstants;
import cn.fish.initDB.constants.ContextualizeChartConstants;
import cn.fish.initDB.event.ChartAutoSummarizeEvent;
import cn.fish.initDB.util.ChartConversationUtils;
import cn.fish.initDB.workflow.agent.tool.AgentAbstractTool;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChartEventListener {

    private final BaseCheckpointSaver baseCheckpointSaver;
    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatModelUsageRecorder chatModelUsageRecorder;

    private static final ConcurrentHashMap<String, Object> SESSION_LOCKS = new ConcurrentHashMap<>();

    /** 压缩摘要写入 checkpoint 时，作为首条 User 消息的固定前缀（非 classpath 提示词模板）。 */
    private static final String CHART_COMPRESSED_SUMMARY_USER_PREFIX =
            "以下为此前多轮对话的压缩摘要，请在回答时保留其中涉及的业务与库表信息：\n\n";

    /** 仅串行化同 thread 的自动命名逻辑，不与 {@link #SESSION_LOCKS} 共用，避免等待摘要压缩持锁过久。 */
    private static final ConcurrentHashMap<String, Object> SESSION_TITLE_LOCKS = new ConcurrentHashMap<>();

    public ChartEventListener(
            ChatModel chatModel,
            BaseCheckpointSaver baseCheckpointSaver,
            ApplicationPromptTemplates applicationPromptTemplates,
            ChatSessionRepository chatSessionRepository,
            ChatModelUsageRecorder chatModelUsageRecorder) {
        this.chatModel = chatModel;
        this.baseCheckpointSaver = baseCheckpointSaver;
        this.applicationPromptTemplates = applicationPromptTemplates;
        this.chatSessionRepository = chatSessionRepository;
        this.chatModelUsageRecorder = chatModelUsageRecorder;
    }

    private Object lockForThread(String threadId) {
        return SESSION_LOCKS.computeIfAbsent(threadId, k -> new Object());
    }

    private Object titleLockForThread(String threadId) {
        return SESSION_TITLE_LOCKS.computeIfAbsent(threadId, k -> new Object());
    }

    @Async("initDbExecutor")
    @EventListener
    public void autoSummarizeEvent(ChartAutoSummarizeEvent event) {
        RunnableConfig config = event.getConfig();
        String threadId = config.threadId().orElse(null);
        if (ObjectUtil.isNull(threadId)) {
            return;
        }
        synchronized (lockForThread(threadId)) {
            try {
                Optional<Checkpoint> latestOpt = baseCheckpointSaver.get(config);
                if (latestOpt.isEmpty()) {
                    return;
                }
                Checkpoint latest = latestOpt.get();
                String checkpointIdAtStart = latest.getId();
                List<Message> messages = ChartConversationUtils.copyMessagesFromState(latest.getState());
                if (messages.size() < 12) {
                    return;
                }
                int split = ChartConversationUtils.resolveCompressSplitIndex(
                        messages, 6);
                if (split < 1) {
                    log.info(
                            "Skip compress: no tool-safe split (would orphan ToolResponse or truncate tool round), thread={}",
                            threadId);
                    return;
                }
                List<Message> head = messages.subList(0, split);
                List<Message> tail = new ArrayList<>(messages.subList(split, messages.size()));

                String historyBlock = ChartConversationUtils.buildHistoryText(head);
                if (historyBlock.length() > 14_000) {
                    historyBlock = historyBlock.substring(0, 14_000)
                            + ContextualizeChartConstants.CHART_SUMMARY_TRUNCATED_SUFFIX;
                }
                String fullPrompt = applicationPromptTemplates.renderChartConversationSummary(historyBlock);
                long t0 = System.nanoTime();
                ChatResponse summaryCr = chatModel.call(new Prompt(fullPrompt));
                chatModelUsageRecorder.record("chart_conversation_summary", summaryCr, System.nanoTime() - t0, threadId);
                String summary = summaryCr.getResult().getOutput().getText();
                if (StrUtil.isEmpty(summary)) {
                    log.warn("Auto summary empty, skip checkpoint update");
                    return;
                }

                Optional<Checkpoint> again = baseCheckpointSaver.get(config);
                if (again.isEmpty()
                        || !ObjectUtil.equal(checkpointIdAtStart, again.get().getId())
                        || ChartConversationUtils.copyMessagesFromState(again.get().getState()).size()
                                != messages.size()) {
                    log.info(
                            "Skip compress: checkpoint or messages changed during summarization (thread={})",
                            threadId);
                    return;
                }
                String trimSummary = summary.trim();
                List<Message> compressed = new ArrayList<>();
                compressed.add(new UserMessage(CHART_COMPRESSED_SUMMARY_USER_PREFIX + trimSummary));
                compressed.addAll(tail);

                Map<String, Object> newState = new HashMap<>(again.get().getState());
                newState.put(WorkflowConstants.STATE_KEY_MESSAGES, compressed);

                Checkpoint updated = Checkpoint.builder()
                                               .id(again.get().getId())
                                               .state(newState)
                                               .nodeId(again.get().getNodeId())
                                               .nextNodeId(again.get().getNextNodeId())
                                               .build();

                RunnableConfig putConfig = RunnableConfig.builder(config).checkPointId(again.get().getId()).build();
                baseCheckpointSaver.put(putConfig, updated);
                log.info(
                        "Auto summary applied: thread={}, messages {} -> {}",
                        threadId,
                        messages.size(),
                        compressed.size());
                if (log.isDebugEnabled()) {
                    log.debug(trimSummary);
                }
            } catch (Exception e) {
                log.warn("Failed to auto summarize / compress checkpoint", e);
            }
        }
    }

    /**
     * 在每次对话流结束后（与压缩摘要同一事件）尝试自动命名：
     * <ul>
     *   <li>占位名（「新的对话…」）时，首次有可用片段即调用模型；</li>
     *   <li>否则每隔 3 次流结束才再次调用模型，避免每轮都打标题。</li>
     * </ul>
     * 根据 checkpoint 内前几条消息生成标题并写回 {@code chat_session}。
     * 不与 {@link #autoSummarizeEvent} 共用 {@link #SESSION_LOCKS}，避免压缩持锁期间阻塞命名；同会话命名用 {@link #SESSION_TITLE_LOCKS} 串行。
     */
    @Async("initDbExecutor")
    @EventListener
    public void autoNameSessionAfterChartEvent(ChartAutoSummarizeEvent event) {
        RunnableConfig config = event.getConfig();
        String threadId = config.threadId().orElse(null);
        if (ObjectUtil.isNull(threadId)) {
            return;
        }
        String sessionId = AgentAbstractTool.stripSubGraphCheckpointThreadSuffix(threadId);
        synchronized (titleLockForThread(threadId)) {
            try {
                chatSessionRepository.incrementStreamDone(sessionId);
                ChatSession session = chatSessionRepository.queryUnique(sessionId);
                if (ObjectUtil.isNull(session)) {
                    return;
                }
                int completed = ObjectUtil.defaultIfNull(session.getStreamDone(), 0);
                int lastTitleAt = ObjectUtil.defaultIfNull(session.getNamedStream(), 0);
                boolean placeholder = ChartConversationUtils.isPlaceholderSessionName(session.getSessionName());
                boolean shouldCallModel =
                        placeholder || (completed - lastTitleAt >= 3);
                if (!shouldCallModel) {
                    return;
                }
                Optional<Checkpoint> latestOpt = baseCheckpointSaver.get(config);
                if (latestOpt.isEmpty()) {
                    return;
                }
                List<Message> messages = ChartConversationUtils.copyMessagesFromState(latestOpt.get().getState());
                String snippet = ChartConversationUtils.buildSnippetForSessionTitle(messages);
                if (StrUtil.isBlank(snippet)) {
                    return;
                }
                String prompt = applicationPromptTemplates.renderChartSessionTitle(snippet);
                long t0 = System.nanoTime();
                ChatResponse titleCr = chatModel.call(new Prompt(prompt));
                chatModelUsageRecorder.record("chart_session_title", titleCr, System.nanoTime() - t0, threadId);
                String rawTitle = titleCr.getResult().getOutput().getText();
                String title = ChartConversationUtils.sanitizeSessionTitle(rawTitle);
                if (StrUtil.isBlank(title) || title.length() < 2) {
                    log.warn("Auto session title empty or too short, skip sessionId={}", sessionId);
                    return;
                }
                String currentName = StrUtil.trimToEmpty(session.getSessionName());
                if (ObjectUtil.equal(title, currentName)) {
                    session.setNamedStream(completed);
                    chatSessionRepository.update(session);
                    return;
                }
                session.setSessionName(title);
                session.setNamedStream(completed);
                chatSessionRepository.update(session);
                log.info("Auto session title set sessionId={} title={}", sessionId, title);
            } catch (Exception e) {
                log.warn("Failed to auto-name session sessionId={}", sessionId, e);
            }
        }
    }
}
