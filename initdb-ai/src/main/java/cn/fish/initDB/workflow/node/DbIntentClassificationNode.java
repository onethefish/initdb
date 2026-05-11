package cn.fish.initDB.workflow.node;

import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.util.ExplicitSqlUserInput;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路由：用户<strong>明确写出</strong>可执行 SELECT/WITH 时走直连；否则由模型在 DIRECT（单表明细拉取）与 REACT（分析、结构、多步等）之间二选一。
 */
@Slf4j
@Component
public class DbIntentClassificationNode implements NodeAction {

    private static final Pattern ROUTE_TOKEN = Pattern.compile("\\b(DIRECT|REACT)\\b", Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;

    public DbIntentClassificationNode(ChatModel chatModel, ApplicationPromptTemplates applicationPromptTemplates) {
        this.chatModel = chatModel;
        this.applicationPromptTemplates = applicationPromptTemplates;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String standalone = state.value(InitDBConstants.STANDALONE, "");
        String route = classifyRoute(standalone);
        log.debug("db intent route={} for question snippet: {}", route, abbreviate(standalone));
        return DbWorkflowBundle.writeBundle(state, b -> b.put(InitDBConstants.STATE_KEY_DB_ROUTE, route));
    }

    private String classifyRoute(String standalone) {
        if (StrUtil.isBlank(standalone)) {
            return InitDBConstants.ROUTE_REACT_VALUE;
        }
        if (ExplicitSqlUserInput.matches(standalone)) {
            return InitDBConstants.ROUTE_DIRECT_DATA_VALUE;
        }
        String body = clampStandalone(standalone.trim());
        try {
            String raw = chatModel.call(new Prompt(applicationPromptTemplates.renderDbIntentRoute(body)))
                                  .getResult()
                                  .getOutput()
                                  .getText();
            String route = parseLlmRouteLabel(raw);
            log.debug("db intent llm raw={} -> {}", abbreviate(raw), route);
            return route;
        } catch (Exception e) {
            log.warn("db intent route LLM failed, fallback react: {}", e.toString());
            return InitDBConstants.ROUTE_REACT_VALUE;
        }
    }

    private static String parseLlmRouteLabel(String raw) {
        if (StrUtil.isBlank(raw)) {
            return InitDBConstants.ROUTE_REACT_VALUE;
        }
        Matcher m = ROUTE_TOKEN.matcher(raw);
        if (m.find()) {
            return "DIRECT".equalsIgnoreCase(m.group(1))
                    ? InitDBConstants.ROUTE_DIRECT_DATA_VALUE
                    : InitDBConstants.ROUTE_REACT_VALUE;
        }
        return InitDBConstants.ROUTE_REACT_VALUE;
    }

    private static String clampStandalone(String s) {
        int max = InitDBConstants.CONTEXTUALIZE_BODY_MAX_CHARS;
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }

    private static String abbreviate(String s) {
        if (ObjectUtil.isNull(s)) {
            return "";
        }
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }
}
