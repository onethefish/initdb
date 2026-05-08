package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChatRequest;
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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 结合 checkpoint 中的历史与本轮输入，生成可独立理解的问题表述，供数据库 Agent 使用。
 */
@Slf4j
@Service
public class ContextualizeServiceImpl {

    private static final int MAX_HISTORY_CHARS = 4_000;

    private static final String REWRITE_SYSTEM = """
            你是「问句补全」助手，服务于数据库对话系统。你会收到一段此前多轮对话摘录，以及用户最新一句输入。
            任务：把「最新输入」改写成一条**独立可理解**的中文问题或指令；若其中指代词（如「那张表」「同上」「再查一下」）依赖上文，请根据摘录补全实体与意图。
            要求：
            - 只输出一行改写结果，不要解释、不要引号、不要 Markdown。
            - 若最新输入本身已自洽，可原样返回或只做标点/空白修整。
            - 不要编造摘录中未出现的表名或业务事实。
            """;

    private final ChatModel chatModel;
    private final BaseCheckpointSaver checkpointSaver;

    public ContextualizeServiceImpl(ChatModel chatModel, BaseCheckpointSaver checkpointSaver) {
        this.chatModel = chatModel;
        this.checkpointSaver = checkpointSaver;
    }


    public String apply(ChatRequest chatRequest) {
        RunnableConfig config = RunnableConfig.builder()
                                              .threadId(chatRequest.getSessionId())
                                              .mergeReasoningContent(true)
                                              .build();
        String rawUserMessage = chatRequest.getMessage();
        if (StrUtil.isBlank(rawUserMessage)) {
            return "";
        }
        String trimmed = rawUserMessage.trim();
        try {
            List<Message> prior = loadPriorMessages(config);
            if (prior.isEmpty()) {
                return trimmed;
            }
            String historyBlock = buildHistoryBlock(prior);
            if (StrUtil.isBlank(historyBlock)) {
                return trimmed;
            }
            String userBlock = "-----对话摘录-----\n"
                    + historyBlock
                    + "\n-----最新输入-----\n"
                    + trimmed
                    + "\n-----请只输出改写后的一行-----";
            String fullPrompt = REWRITE_SYSTEM + "\n\n" + userBlock;
            String out = chatModel.call(new Prompt(fullPrompt)).getResult().getOutput().getText();
            String cleaned = StrUtil.trimToEmpty(out).replaceAll("(?s)^\\s*[\"'「]|[\"'」]\\s*$", "");
            if (StrUtil.isBlank(cleaned)) {
                return trimmed;
            }
            int nl = cleaned.indexOf('\n');
            if (nl > 0) {
                cleaned = cleaned.substring(0, nl).trim();
            }
            if (cleaned.length() > 2_000) {
                cleaned = cleaned.substring(0, 2_000);
            }
            log.info("Contextualize result:" + cleaned);
            return cleaned;
        } catch (Exception e) {
            log.warn("Contextualize failed, using raw user message: {}", e.toString());
            return trimmed;
        }
    }

    private List<Message> loadPriorMessages(RunnableConfig config) {
        Optional<Checkpoint> cp = checkpointSaver.get(config);
        return cp.map(checkpoint -> copyMessagesFromState(checkpoint.getState())).orElseGet(List::of);
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
        List<Message> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Message m) {
                out.add(m);
            }
        }
        return out;
    }

    private static String buildHistoryBlock(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        int n = messages.size();
        int start = Math.max(0, n - 20);
        for (int i = start; i < n; i++) {
            sb.append(messageToLine(messages.get(i))).append('\n');
        }
        String s = sb.toString();
        if (s.length() > MAX_HISTORY_CHARS) {
            return s.substring(s.length() - MAX_HISTORY_CHARS);
        }
        return s;
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
        return m.getClass().getSimpleName();
    }
}
