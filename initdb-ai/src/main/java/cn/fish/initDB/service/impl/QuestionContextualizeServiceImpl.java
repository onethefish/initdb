package cn.fish.initDB.service.impl;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.entity.QuestionContextualizeResult;
import cn.fish.initDB.service.QuestionContextualizeService;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class QuestionContextualizeServiceImpl implements QuestionContextualizeService {

    private static final String REWRITE_SYSTEM = """
            你是「问句补全」助手，服务于数据库对话系统。你会收到一段此前多轮对话摘录，以及用户最新一句输入。
            任务：把「最新输入」改写成一条**独立可理解**的中文问题或指令；若其中指代词（如「那张表」「同上」「再查一下」）依赖上文，请根据摘录补全实体与意图。
            要求：
            - 只输出一行改写结果，不要解释、不要引号、不要 Markdown。
            - 不要添加「补全的会话」等任何前缀或标签，只输出改写后的句子本身。
            - 若最新输入本身已自洽，可原样返回或只做标点/空白修整。
            - 不要编造摘录中未出现的表名或业务事实。
            """;

    private static final String USER_BLOCK_HISTORY = "-----对话摘录-----\n";
    private static final String USER_BLOCK_LATEST = "\n-----最新输入-----\n";
    private static final String USER_BLOCK_TAIL = "\n-----请只输出改写后的一行-----";

    private final ChatModel chatModel;
    private final BaseCheckpointSaver checkpointSaver;

    public QuestionContextualizeServiceImpl(ChatModel chatModel, BaseCheckpointSaver checkpointSaver) {
        this.chatModel = chatModel;
        this.checkpointSaver = checkpointSaver;
    }

    @Override
    public QuestionContextualizeResult rewrite(String rawMessage, String sessionId) {
        String raw = rawMessage == null ? "" : rawMessage;
        if (StrUtil.isBlank(raw)) {
            return new QuestionContextualizeResult("", "");
        }
        String trimmed = raw.trim();
        String sid = StrUtil.trimToEmpty(sessionId);
        RunnableConfig checkpointConfig = RunnableConfig.builder()
                                                        .threadId(sid)
                                                        .mergeReasoningContent(true)
                                                        .build();

        List<Message> prior = loadPriorMessages(checkpointSaver, checkpointConfig);
        if (prior.isEmpty()) {
            return withDisplay(trimmed);
        }
        String historyBlock = buildHistoryBlock(prior);
        if (StrUtil.isBlank(historyBlock)) {
            return withDisplay(trimmed);
        }
        String userBlock = USER_BLOCK_HISTORY + historyBlock + USER_BLOCK_LATEST + trimmed + USER_BLOCK_TAIL;

        String rawText;
        try {
            rawText = chatModel.call(new Prompt(List.of(new SystemMessage(REWRITE_SYSTEM), new UserMessage(userBlock))))
                               .getResult()
                               .getOutput()
                               .getText();
        } catch (Exception e) {
            log.warn("Contextualize LLM call failed, using trimmed input: {}", e.getMessage());
            return withDisplay(trimmed);
        }

        String cleaned = StrUtil.trimToEmpty(rawText).replaceAll("(?s)^\\s*[\"'「]|[\"'」]\\s*$", "");
        if (StrUtil.isBlank(cleaned)) {
            return withDisplay(trimmed);
        }
        int nl = cleaned.indexOf('\n');
        if (nl > 0) {
            cleaned = cleaned.substring(0, nl).trim();
        }
        if (cleaned.length() > InitDBConstants.CONTEXTUALIZE_BODY_MAX_CHARS) {
            cleaned = cleaned.substring(0, InitDBConstants.CONTEXTUALIZE_BODY_MAX_CHARS);
        }
        String body = stripDisplayPrefix(cleaned);
        log.info("Question contextualize result (body): {}", body);
        return withDisplay(body);
    }

    private static QuestionContextualizeResult withDisplay(String bodyForAgent) {
        String body = StrUtil.trimToEmpty(bodyForAgent);
        String display = body.isEmpty() ? "" : body;
        return new QuestionContextualizeResult(body, display);
    }

    private static String stripDisplayPrefix(String cleaned) {
        String s = StrUtil.trimToEmpty(cleaned);
        for (String p : List.of(
                InitDBConstants.CONTEXTUALIZE_DISPLAY_PREFIX,
                InitDBConstants.CONTEXTUALIZE_DISPLAY_PREFIX_ASCII_COLON)) {
            if (s.startsWith(p)) {
                return StrUtil.trimToEmpty(s.substring(p.length()));
            }
        }
        return s;
    }

    private static List<Message> loadPriorMessages(BaseCheckpointSaver checkpointSaver, RunnableConfig config) {
        Optional<Checkpoint> cp = checkpointSaver.get(config);
        return cp.map(checkpoint -> copyMessagesFromState(checkpoint.getState())).orElseGet(List::of);
    }

    @SuppressWarnings("unchecked")
    private static List<Message> copyMessagesFromState(Map<String, Object> checkpointState) {
        if (checkpointState == null) {
            return List.of();
        }
        Object raw = checkpointState.get(InitDBConstants.STATE_KEY_MESSAGES);
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
        int start = Math.max(0, n - InitDBConstants.CONTEXTUALIZE_MAX_PRIOR_MESSAGES);
        for (int i = start; i < n; i++) {
            sb.append(messageToLine(messages.get(i))).append('\n');
        }
        String s = sb.toString();
        if (s.length() > InitDBConstants.CONTEXTUALIZE_MAX_HISTORY_CHARS) {
            return s.substring(s.length() - InitDBConstants.CONTEXTUALIZE_MAX_HISTORY_CHARS);
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
