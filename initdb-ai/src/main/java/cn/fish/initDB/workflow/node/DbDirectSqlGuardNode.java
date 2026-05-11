package cn.fish.initDB.workflow.node;

import cn.fish.initDB.workflow.DbWorkflowBundle;
import cn.fish.initDB.workflow.agent.tool.QuerySqlCheckTool;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 直连链路：调用与 Agent 相同的 SQL 校验逻辑，写入 {@link #DB_BUNDLE_KEY_SQL_GUARD} 与失败时的 {@link cn.fish.initDB.workflow.DbWorkflowBundle#DB_BUNDLE_KEY_DIRECT_ANSWER}。
 */
@Slf4j
@Component
public class DbDirectSqlGuardNode implements NodeAction {

    // StateGraph 节点 id，勿改字符串以免破坏 checkpoint / 流式帧匹配
    public static final String GRAPH_NODE_ID = "db_direct_sql_guard";

    /** {@link cn.fish.initDB.workflow.DbWorkflowBundle#BUNDLE_STATE_KEY} 内：SQL 校验是否通过（布尔）。 */
    public static final String DB_BUNDLE_KEY_SQL_GUARD = "db_sql_guard";

    private static final ToolContext EMPTY_TOOL_CONTEXT = new ToolContext(new HashMap<>());

    private final QuerySqlCheckTool querySqlCheckTool;

    public DbDirectSqlGuardNode(QuerySqlCheckTool querySqlCheckTool) {
        this.querySqlCheckTool = querySqlCheckTool;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String sql = DbWorkflowBundle.bundleString(DbWorkflowBundle.readCopy(state), DbDirectNl2SqlNode.DB_BUNDLE_KEY_GENERATED_SQL, "");
        if (StrUtil.isBlank(sql)) {
            return DbWorkflowBundle.writeBundle(state, b -> {
                b.put(DB_BUNDLE_KEY_SQL_GUARD, Boolean.FALSE);
                b.put(DbWorkflowBundle.DB_BUNDLE_KEY_DIRECT_ANSWER, "未生成 SQL，已终止查询。");
            });
        }
        String verdict = querySqlCheckTool.apply(new QuerySqlCheckTool.Request(sql), EMPTY_TOOL_CONTEXT);
        boolean ok = ObjectUtil.isNotNull(verdict) && verdict.contains("校验成功");
        log.info("db direct sql_guard ok={}", ok);
        return DbWorkflowBundle.writeBundle(state, b -> {
            b.put(DB_BUNDLE_KEY_SQL_GUARD, ok);
            if (!ok) {
                b.put(DbWorkflowBundle.DB_BUNDLE_KEY_DIRECT_ANSWER, "## SQL 校验未通过\n\n" + verdict);
            }
        });
    }
}
