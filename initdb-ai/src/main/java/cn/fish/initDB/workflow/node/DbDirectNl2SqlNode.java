package cn.fish.initDB.workflow.node;

import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.WorkflowConstants;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直连查数链路：从用户表述生成单条 SELECT，或识别用户粘贴的 SQL。
 */
@Slf4j
@Component
public class DbDirectNl2SqlNode implements NodeAction {

    // StateGraph 节点 id，勿改字符串以免破坏 checkpoint / 流式帧匹配
    public static final String GRAPH_NODE_ID = "db_direct_nl2sql";

    /** {@link cn.fish.initDB.workflow.DbWorkflowBundle#BUNDLE_STATE_KEY} 内：直连链路生成的 SQL 文本。 */
    public static final String DB_BUNDLE_KEY_GENERATED_SQL = "db_generated_sql";

    private static final Pattern CODE_FENCE_SQL = Pattern.compile("(?is)```(?:sql)?\\s*([\\s\\S]*?)```");
    private static final Pattern LEADING_SELECT = Pattern.compile("(?is)^\\s*(SELECT\\b[\\s\\S]+)$");

    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;

    public DbDirectNl2SqlNode(ChatModel chatModel, ApplicationPromptTemplates applicationPromptTemplates) {
        this.chatModel = chatModel;
        this.applicationPromptTemplates = applicationPromptTemplates;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String standalone = state.value(WorkflowConstants.STANDALONE, "").trim();
        String catalogJson = DbWorkflowBundle.bundleString(
                DbWorkflowBundle.readCopy(state), DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG_JSON, "[]");
        String sql = resolveSql(standalone, catalogJson);
        log.info("db direct nl2sql length={}", StrUtil.length(sql));
        return DbWorkflowBundle.writeBundle(state, b -> b.put(DB_BUNDLE_KEY_GENERATED_SQL, sql));
    }

    private String resolveSql(String text, String tableCatalogJson) {
        if (StrUtil.isBlank(text)) {
            return "SELECT 1 AS placeholder";
        }
        Matcher fence = CODE_FENCE_SQL.matcher(text);
        if (fence.find()) {
            return normalizeOneStatement(fence.group(1));
        }
        Matcher sel = LEADING_SELECT.matcher(text.trim());
        if (sel.matches()) {
            return normalizeOneStatement(sel.group(1));
        }
        try {
            String raw = chatModel.call(new Prompt(applicationPromptTemplates.renderDbDirectNl2sql(text, tableCatalogJson)))
                                  .getResult()
                                  .getOutput()
                                  .getText();
            return normalizeOneStatement(stripNoise(raw));
        } catch (Exception e) {
            log.warn("nl2sql model failed", e);
            return "SELECT 1 AS placeholder";
        }
    }

    private static String stripNoise(String raw) {
        if (ObjectUtil.isNull(raw)) {
            return "SELECT 1 AS placeholder";
        }
        Matcher fence = CODE_FENCE_SQL.matcher(raw);
        if (fence.find()) {
            return fence.group(1).trim();
        }
        return raw.trim();
    }

    private static String normalizeOneStatement(String sql) {
        if (ObjectUtil.isNull(sql)) {
            return "SELECT 1 AS placeholder";
        }
        String s = sql.trim();
        if (s.endsWith(";")) {
            s = s.substring(0, s.length() - 1).trim();
        }
        int idx = s.toLowerCase().indexOf("\nselect ");
        if (idx > 0) {
            s = s.substring(idx).trim();
        }
        return s;
    }
}
