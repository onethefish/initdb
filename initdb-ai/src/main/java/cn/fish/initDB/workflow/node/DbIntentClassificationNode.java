package cn.fish.initDB.workflow.node;

import cn.fish.common.ai.ChatModelUsageRecorder;
import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.WorkflowConstants;
import cn.fish.initDB.constants.ContextualizeChartConstants;
import cn.fish.initDB.util.ExplicitSqlUserInput;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
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

    // 父图 StateGraph 节点 id，勿改字符串以免破坏 checkpoint / 流式帧匹配
    public static final String GRAPH_NODE_ID = "db_intent_classification";

    /** {@link cn.fish.initDB.workflow.DbWorkflowBundle#BUNDLE_STATE_KEY} 内：直连 / ReAct 分支（布尔）。 */
    public static final String DB_BUNDLE_KEY_ROUTE = "db_route";

    private static final Pattern ROUTE_TOKEN = Pattern.compile("\\b(DIRECT|REACT)\\b", Pattern.CASE_INSENSITIVE);

    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;
    private final ChatModelUsageRecorder chatModelUsageRecorder;

    public DbIntentClassificationNode(ChatModel chatModel, ApplicationPromptTemplates applicationPromptTemplates,
                                      ChatModelUsageRecorder chatModelUsageRecorder) {
        this.chatModel = chatModel;
        this.applicationPromptTemplates = applicationPromptTemplates;
        this.chatModelUsageRecorder = chatModelUsageRecorder;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String standalone = state.value(WorkflowConstants.STANDALONE, "");
        boolean useDirectData = classifyUseDirectData(standalone);
        log.debug("db intent useDirectData={} for question snippet: {}", useDirectData, abbreviate(standalone));
        return DbWorkflowBundle.writeBundle(state, b -> b.put(DB_BUNDLE_KEY_ROUTE, useDirectData));
    }

    private boolean classifyUseDirectData(String standalone) {
        if (StrUtil.isBlank(standalone)) {
            return false;
        }
        if (ExplicitSqlUserInput.matches(standalone)) {
            return true;
        }
        String body = clampStandalone(standalone.trim());
        try {
            long t0 = System.nanoTime();
            ChatResponse cr = chatModel.call(new Prompt(applicationPromptTemplates.renderDbIntentRoute(body)));
            chatModelUsageRecorder.record("db_intent_route", cr, System.nanoTime() - t0, null);
            String raw = cr.getResult().getOutput().getText();
            boolean useDirect = parseLlmWantsDirectData(raw);
            log.debug("db intent llm raw={} -> {}", abbreviate(raw), useDirect);
            return useDirect;
        } catch (Exception e) {
            log.warn("db intent route LLM failed, fallback react: {}", e.toString());
            return false;
        }
    }

    private static boolean parseLlmWantsDirectData(String raw) {
        if (StrUtil.isBlank(raw)) {
            return false;
        }
        Matcher m = ROUTE_TOKEN.matcher(raw);
        if (m.find()) {
            return "DIRECT".equalsIgnoreCase(m.group(1));
        }
        return false;
    }

    private static String clampStandalone(String s) {
        int max = ContextualizeChartConstants.CONTEXTUALIZE_BODY_MAX_CHARS;
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
