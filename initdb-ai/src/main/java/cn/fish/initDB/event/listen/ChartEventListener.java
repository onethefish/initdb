package cn.fish.initDB.event.listen;

import cn.fish.initDB.event.ChartAutoSummarizeEvent;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ChartEventListener {

    /**
     * 消息条数达到该值才压缩（含用户/助手/工具等）。
     * 须大于 {@link #KEEP_RECENT_MESSAGES}，否则拆分点无意义。
     */
    private static final int COMPRESS_MIN_MESSAGES = 12;
    /**
     * 压缩后保留的最近消息条数，需覆盖常见「模型 + 工具」多轮结构。
     */
    private static final int KEEP_RECENT_MESSAGES = 6;
    private static final int MAX_CHARS_FOR_SUMMARY_INPUT = 14_000;

    private static final String SUMMARY_PROMPT = """
            你是对话总结助手。将以下对话历史总结为简洁的要点:
            - 用户的核心需求
            - 涉及的关键表和字段
            - 重要的SQL查询或结论

            要求:
            - 用中文总结
            - 控制在100字以内
            - 保留关键技术细节
            """;

    private final BaseCheckpointSaver baseCheckpointSaver;
    private final ChatModel chatModel;

    private static final ConcurrentHashMap<String, Object> SESSION_LOCKS = new ConcurrentHashMap<>();

    public ChartEventListener(ChatModel chatModel, BaseCheckpointSaver baseCheckpointSaver) {
        this.chatModel = chatModel;
        this.baseCheckpointSaver = baseCheckpointSaver;
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
                if (messages.size() < COMPRESS_MIN_MESSAGES) {
                    return;
                }
                int split = resolveCompressSplitIndex(messages, KEEP_RECENT_MESSAGES);
                if (split < 1) {
                    log.info(
                            "Skip compress: no tool-safe split (would orphan ToolResponse or truncate tool round), thread={}",
                            threadId);
                    return;
                }
                List<Message> head = messages.subList(0, split);
                List<Message> tail = new ArrayList<>(messages.subList(split, messages.size()));

                String historyBlock = buildHistoryText(head);
                if (historyBlock.length() > MAX_CHARS_FOR_SUMMARY_INPUT) {
                    historyBlock = historyBlock.substring(0, MAX_CHARS_FOR_SUMMARY_INPUT) + "\n...[truncated]";
                }
                String fullPrompt = SUMMARY_PROMPT + "\n\n---对话记录---\n" + historyBlock;
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
                compressed.add(
                        new UserMessage(
                                "以下为此前多轮对话的压缩摘要，请在回答时保留其中涉及的业务与库表信息：\n\n"
                                        + trimSummary));
                compressed.addAll(tail);

                Map<String, Object> newState = new HashMap<>(again.get().getState());
                newState.put("messages", compressed);

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

    @SuppressWarnings("unchecked")
    private static List<Message> copyMessagesFromState(Map<String, Object> state) {
        if (state == null) {
            return List.of();
        }
        Object raw = state.get("messages");
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
     * 在「保留最近 keepRecent 条」基础上向左扩展切分点，避免：
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
