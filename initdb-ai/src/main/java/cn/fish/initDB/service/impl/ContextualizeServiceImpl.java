package cn.fish.initDB.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.service.ContextualizeService;
import cn.fish.initDB.util.ExplicitSqlUserInput;
import cn.hutool.core.util.ObjectUtil;
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
public class ContextualizeServiceImpl implements ContextualizeService {

    private final ChatModel chatModel;
    private final BaseCheckpointSaver checkpointSaver;
    private final ApplicationPromptTemplates applicationPromptTemplates;

    public ContextualizeServiceImpl(ChatModel chatModel, BaseCheckpointSaver checkpointSaver,
                                    ApplicationPromptTemplates applicationPromptTemplates) {
        this.chatModel = chatModel;
        this.checkpointSaver = checkpointSaver;
        this.applicationPromptTemplates = applicationPromptTemplates;
    }

    @Override
    public String rewrite(String rawMessage, String sessionId) {
        if (StrUtil.isBlank(rawMessage)) {
            return rawMessage;
        }
        if (StrUtil.isBlank(sessionId)) {
            throw new CommonException("sessionId 不能为空");
        }
        String trimmed = rawMessage.trim();
        if (ExplicitSqlUserInput.matches(trimmed)) {
            log.debug("Skip contextualize rewrite: explicit SQL input");
            return trimmed;
        }

        RunnableConfig checkpointConfig = RunnableConfig.builder()
                                                        .threadId(sessionId)
                                                        .mergeReasoningContent(true)
                                                        .build();

        List<Message> prior = loadPriorMessages(checkpointSaver, checkpointConfig);
        if (prior.isEmpty()) {
            return trimmed;
        }
        String historyBlock = buildHistoryBlock(prior);
        if (StrUtil.isBlank(historyBlock)) {
            return trimmed;
        }
        String userBlock = applicationPromptTemplates.renderContextualizeUserBlock(historyBlock, trimmed);

        String rawText;
        try {
            rawText = chatModel.call(new Prompt(List.of(
                                       new SystemMessage(applicationPromptTemplates.contextualizeRewriteSystemText()),
                                       new UserMessage(userBlock))))
                               .getResult()
                               .getOutput()
                               .getText();
        } catch (Exception e) {
            log.warn("Contextualize LLM call failed, using trimmed input: {}", e.getMessage());
            return trimmed;
        }

        String cleaned = StrUtil.trimToEmpty(rawText).replaceAll("(?s)^\\s*[\"'「]|[\"'」]\\s*$", "");
        if (StrUtil.isBlank(cleaned)) {
            return trimmed;
        }
        int nl = cleaned.indexOf('\n');
        if (nl > 0) {
            cleaned = cleaned.substring(0, nl).trim();
        }
        if (cleaned.length() > InitDBConstants.CONTEXTUALIZE_BODY_MAX_CHARS) {
            cleaned = cleaned.substring(0, InitDBConstants.CONTEXTUALIZE_BODY_MAX_CHARS);
        }
        log.info("Question contextualize result: {}", cleaned);
        return cleaned;
    }

    private static List<Message> loadPriorMessages(BaseCheckpointSaver checkpointSaver, RunnableConfig config) {
        Optional<Checkpoint> cp = checkpointSaver.get(config);
        return cp.map(checkpoint -> copyMessagesFromState(checkpoint.getState())).orElseGet(List::of);
    }

    private static List<Message> copyMessagesFromState(Map<String, Object> checkpointState) {
        if (ObjectUtil.isNull(checkpointState)) {
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
