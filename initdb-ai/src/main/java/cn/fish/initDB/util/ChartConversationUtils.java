package cn.fish.initDB.util;

import cn.fish.initDB.constants.WorkflowConstants;
import cn.fish.initDB.constants.ContextualizeChartConstants;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chart 对话 checkpoint 中的消息：读取 state、拼历史文本、压缩切分、会话标题 snippet 等纯逻辑。
 */
public final class ChartConversationUtils {

    private ChartConversationUtils() {
    }

    public static boolean isPlaceholderSessionName(String sessionName) {
        String n = StrUtil.trimToEmpty(sessionName);
        if (n.isEmpty()) {
            return true;
        }
        return StrUtil.startWith(n, "新的对话");
    }

    /**
     * @param maxMessages            参与 snippet 的消息条数上限（≥1）
     * @param maxChars               snippet 字符上限（调用方宜保证 ≥ 数百）
     * @param messageSliceFromTail   true：取列表末尾 maxMessages 条；false：取开头 maxMessages 条
     * @param charsTruncateTail      超长时 true：保留末尾 maxChars 字符并加「更早省略」前缀；false：截头 + 截断后缀
     */
    public static String buildSnippetForSessionTitle(
            List<Message> messages,
            int maxMessages,
            int maxChars,
            boolean messageSliceFromTail,
            boolean charsTruncateTail) {
        if (CollUtil.isEmpty(messages)) {
            return "";
        }
        int cap = Math.max(1, maxMessages);
        int n = Math.min(messages.size(), cap);
        List<Message> slice;
        if (messageSliceFromTail && messages.size() > n) {
            slice = messages.subList(messages.size() - n, messages.size());
        } else {
            slice = messages.subList(0, n);
        }
        String block = buildHistoryText(slice);
        block = StrUtil.trim(block);
        if (block.isEmpty()) {
            return "";
        }
        int max = Math.max(200, maxChars);
        if (block.length() > max) {
            if (charsTruncateTail) {
                return ContextualizeChartConstants.CHART_SUMMARY_HEAD_OMITTED_PREFIX
                        + block.substring(block.length() - max);
            }
            return block.substring(0, max) + ContextualizeChartConstants.CHART_SUMMARY_TRUNCATED_SUFFIX;
        }
        return block;
    }

    public static String sanitizeSessionTitle(String raw) {
        if (ObjectUtil.isNull(raw)) {
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
        int cap = 48;
        if (t.length() > cap) {
            t = t.substring(0, cap).strip();
        }
        return t;
    }

    @SuppressWarnings("unchecked")
    public static List<Message> copyMessagesFromState(Map<String, Object> state) {
        if (ObjectUtil.isNull(state)) {
            return List.of();
        }
        Object raw = state.get(WorkflowConstants.STATE_KEY_MESSAGES);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return new ArrayList<>((List<Message>) (List<?>) list);
    }

    public static String buildHistoryText(List<Message> head) {
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
     * 在「保留最近 {@code keepRecent} 条」基础上向左扩展切分点，避免：
     * <ul>
     *   <li>后缀以 {@link ToolResponseMessage} 开头（工具结果失去对应的 assistant tool_calls）；</li>
     *   <li>后缀内某条带 tool_calls 的 {@link AssistantMessage} 所需的 tool response 被切到 head 中。</li>
     * </ul>
     * 否则会话在压缩后发给模型时容易整流为空，触发 graph 中 {@code Empty flux detected for key 'messages'}。
     */
    public static int resolveCompressSplitIndex(List<Message> messages, int keepRecent) {
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
