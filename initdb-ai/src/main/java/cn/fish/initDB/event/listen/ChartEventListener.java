package cn.fish.initDB.event.listen;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.event.ChartAutoSummarizeEvent;
import cn.fish.initDB.workflow.agent.tool.AgentAbstractTool;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChartEventListener {

    private final BaseCheckpointSaver baseCheckpointSaver;
    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;
    private final ChatSessionRepository chatSessionRepository;

    private static final ConcurrentHashMap<String, Object> SESSION_LOCKS = new ConcurrentHashMap<>();

    public ChartEventListener(
            ChatModel chatModel,
            BaseCheckpointSaver baseCheckpointSaver,
            ApplicationPromptTemplates applicationPromptTemplates,
            ChatSessionRepository chatSessionRepository) {
        this.chatModel = chatModel;
        this.baseCheckpointSaver = baseCheckpointSaver;
        this.applicationPromptTemplates = applicationPromptTemplates;
        this.chatSessionRepository = chatSessionRepository;
    }

    private static Object lockForThread(String threadId) {
        return SESSION_LOCKS.computeIfAbsent(threadId, k -> new Object());
    }

    @Async("initDbExecutor")
    @EventListener
    public void autoSummarizeEvent(ChartAutoSummarizeEvent event) {
        RunnableConfig config = event.getConfig();
        String threadId = config.threadId().orElse(null);
        if (threadId == null) {
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
                List<Message> messages = copyMessagesFromState(latest.getState());
                if (messages.size() < InitDBConstants.CHART_COMPRESS_MIN_MESSAGES) {
                    return;
                }
                int split = resolveCompressSplitIndex(messages, InitDBConstants.CHART_KEEP_RECENT_MESSAGES);
                if (split < 1) {
                    log.info(
                            "Skip compress: no tool-safe split (would orphan ToolResponse or truncate tool round), thread={}",
                            threadId);
                    return;
                }
                List<Message> head = messages.subList(0, split);
                List<Message> tail = new ArrayList<>(messages.subList(split, messages.size()));

                String historyBlock = buildHistoryText(head);
                if (historyBlock.length() > InitDBConstants.CHART_MAX_CHARS_FOR_SUMMARY_INPUT) {
                    historyBlock = historyBlock.substring(0, InitDBConstants.CHART_MAX_CHARS_FOR_SUMMARY_INPUT)
                            + InitDBConstants.CHART_SUMMARY_TRUNCATED_SUFFIX;
                }
                String fullPrompt = applicationPromptTemplates.renderChartConversationSummary(historyBlock);
                String summary = chatModel.call(new Prompt(fullPrompt)).getResult().getOutput().getText();
                if (StrUtil.isEmpty(summary)) {
                    log.warn("Auto summary empty, skip checkpoint update");
                    return;
                }

                Optional<Checkpoint> again = baseCheckpointSaver.get(config);
                if (again.isEmpty()
                        || !checkpointIdAtStart.equals(again.get().getId())
                        || copyMessagesFromState(again.get().getState()).size() != messages.size()) {
                    log.info(
                            "Skip compress: checkpoint or messages changed during summarization (thread={})",
                            threadId);
                    return;
                }
                String trimSummary = summary.trim();
                List<Message> compressed = new ArrayList<>();
                compressed.add(new UserMessage(applicationPromptTemplates.renderChartCompressedUserMessage(trimSummary)));
                compressed.addAll(tail);

                Map<String, Object> newState = new HashMap<>(again.get().getState());
                newState.put(InitDBConstants.STATE_KEY_MESSAGES, compressed);

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
     * 在每次对话流结束后（与压缩摘要同一事件）尝试自动命名：仅当库中仍为「新的对话…」等占位名时，
     * 根据 checkpoint 内前几条消息调用模型生成标题并写回 {@code chat_session}。
     * 不与 {@link #autoSummarizeEvent} 共用同一把 {@link #SESSION_LOCKS}，避免压缩持锁期间阻塞命名。
     */
    @Async("initDbExecutor")
    @EventListener
    public void autoNameSessionAfterChartEvent(ChartAutoSummarizeEvent event) {
        RunnableConfig config = event.getConfig();
        String threadId = config.threadId().orElse(null);
        if (threadId == null) {
            return;
        }
        String sessionId = AgentAbstractTool.stripSubGraphCheckpointThreadSuffix(threadId);
        try {
            ChatSession session = chatSessionRepository.queryUnique(sessionId);
            if (session == null) {
                return;
            }
            if (!isPlaceholderSessionName(session.getSessionName())) {
                return;
            }
            Optional<Checkpoint> latestOpt = baseCheckpointSaver.get(config);
            if (latestOpt.isEmpty()) {
                return;
            }
            List<Message> messages = copyMessagesFromState(latestOpt.get().getState());
            String snippet = buildSnippetForSessionTitle(messages);
            if (StrUtil.isBlank(snippet)) {
                return;
            }
            String prompt = applicationPromptTemplates.renderChartSessionTitle(snippet);
            String rawTitle = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            String title = sanitizeSessionTitle(rawTitle);
            if (StrUtil.isBlank(title) || title.length() < 2) {
                log.warn("Auto session title empty or too short, skip sessionId={}", sessionId);
                return;
            }
            String currentName = StrUtil.trimToEmpty(session.getSessionName());
            if (title.equals(currentName)) {
                return;
            }
            session.setSessionName(title);
            chatSessionRepository.update(session);
            log.info("Auto session title set sessionId={} title={}", sessionId, title);
        } catch (Exception e) {
            log.warn("Failed to auto-name session sessionId={}", sessionId, e);
        }
    }

    private static boolean isPlaceholderSessionName(String sessionName) {
        String n = StrUtil.trimToEmpty(sessionName);
        if (n.isEmpty()) {
            return true;
        }
        return StrUtil.startWith(n, InitDBConstants.CHAT_SESSION_AUTO_NAME_PLACEHOLDER_PREFIX);
    }

    private static String buildSnippetForSessionTitle(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        int n = Math.min(messages.size(), 10);
        String block = buildHistoryText(messages.subList(0, n));
        block = StrUtil.trim(block);
        if (block.isEmpty()) {
            return "";
        }
        int max = InitDBConstants.CHAT_SESSION_TITLE_SNIPPET_MAX_CHARS;
        if (block.length() > max) {
            return block.substring(0, max) + InitDBConstants.CHART_SUMMARY_TRUNCATED_SUFFIX;
        }
        return block;
    }

    private static String sanitizeSessionTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.strip();
        int nl = t.indexOf('\n');
        if (nl >= 0) {
            t = t.substring(0, nl).strip();
        }
        t = StrUtil.removePrefix(t, "「");
        t = StrUtil.removeSuffix(t, "」");
        t = t.strip();
        while (t.length() >= 2
                && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            t = t.substring(1, t.length() - 1).strip();
        }
        int cap = InitDBConstants.CHAT_SESSION_TITLE_RESULT_MAX_CHARS;
        if (t.length() > cap) {
            t = t.substring(0, cap).strip();
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    private static List<Message> copyMessagesFromState(Map<String, Object> state) {
        if (state == null) {
            return List.of();
        }
        Object raw = state.get(InitDBConstants.STATE_KEY_MESSAGES);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return new ArrayList<>((List<Message>) (List<?>) list);
    }

    private static String buildHistoryText(List<Message> head) {
        StringBuilder sb = new StringBuilder();
        for (Message m : head) {
            sb.append(messageToLine(m)).append('\n');
        }
        return sb.toString();
    }

    private static String messageToLine(Message m) {
        if (m instanceof UserMessage um) {
            return "用户: " + um.getText();
        }
        if (m instanceof AssistantMessage am) {
            return "助手: " + am.getText();
        }
        if (m instanceof ToolResponseMessage trm) {
            return "工具: " + trm.getResponses();
        }
        return m.getClass().getSimpleName() + ": " + m;
    }

    /**
     * 在「保留最近 {@link InitDBConstants#CHART_KEEP_RECENT_MESSAGES} 条」基础上向左扩展切分点，避免：
     * <ul>
     *   <li>后缀以 {@link ToolResponseMessage} 开头（工具结果失去对应的 assistant tool_calls）；</li>
     *   <li>后缀内某条带 tool_calls 的 {@link AssistantMessage} 所需的 tool response 被切到 head 中。</li>
     * </ul>
     * 否则会话在压缩后发给模型时容易整流为空，触发 graph 中 {@code Empty flux detected for key 'messages'}。
     */
    private static int resolveCompressSplitIndex(List<Message> messages, int keepRecent) {
        int n = messages.size();
        int split = n - keepRecent;
        if (split < 1) {
            return -1;
        }
        while (split >= 1 && !isToolSafeMessageSuffix(messages, split)) {
            split--;
        }
        return split >= 1 ? split : -1;
    }

    private static boolean isToolSafeMessageSuffix(List<Message> messages, int split) {
        if (split < 0 || split >= messages.size()) {
            return false;
        }
        if (messages.get(split) instanceof ToolResponseMessage) {
            return false;
        }
        for (int i = split; i < messages.size(); i++) {
            if (!(messages.get(i) instanceof AssistantMessage am) || !am.hasToolCalls()) {
                continue;
            }
            Set<String> pending = new HashSet<>();
            for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                pending.add(tc.id());
            }
            int j = i + 1;
            while (!pending.isEmpty() && j < messages.size()) {
                Message step = messages.get(j);
                if (step instanceof ToolResponseMessage trm) {
                    for (ToolResponseMessage.ToolResponse resp : trm.getResponses()) {
                        pending.remove(resp.id());
                    }
                    j++;
                }
                else {
                    return false;
                }
            }
            if (!pending.isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
