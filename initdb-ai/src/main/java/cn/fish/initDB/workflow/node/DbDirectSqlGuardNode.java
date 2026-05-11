package cn.fish.initDB.workflow.node;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import cn.fish.initDB.workflow.agent.tool.QuerySqlCheckTool;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 直连链路：调用与 Agent 相同的 SQL 校验逻辑，写入 {@link InitDBConstants#STATE_KEY_SQL_GUARD_OK} 与失败时的 {@link InitDBConstants#STATE_KEY_DIRECT_ANSWER}。
 */
@Slf4j
@Component
public class DbDirectSqlGuardNode implements NodeAction {

    private static final ToolContext EMPTY_TOOL_CONTEXT = new ToolContext(Map.of());

    private final QuerySqlCheckTool querySqlCheckTool;

    public DbDirectSqlGuardNode(QuerySqlCheckTool querySqlCheckTool) {
        this.querySqlCheckTool = querySqlCheckTool;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String sql = DbWorkflowBundle.bundleString(DbWorkflowBundle.readCopy(state), InitDBConstants.STATE_KEY_GENERATED_SQL, "");
        if (StrUtil.isBlank(sql)) {
            return DbWorkflowBundle.writeBundle(state, b -> {
                b.put(InitDBConstants.STATE_KEY_SQL_GUARD_OK, Boolean.FALSE);
                b.put(InitDBConstants.STATE_KEY_DIRECT_ANSWER, "未生成 SQL，已终止查询。");
            });
        }
        String verdict = querySqlCheckTool.apply(new QuerySqlCheckTool.Request(sql), EMPTY_TOOL_CONTEXT);
        boolean ok = ObjectUtil.isNotNull(verdict) && verdict.contains("校验成功");
        log.info("db direct sql_guard ok={}", ok);
        return DbWorkflowBundle.writeBundle(state, b -> {
            b.put(InitDBConstants.STATE_KEY_SQL_GUARD_OK, ok);
            if (!ok) {
                b.put(InitDBConstants.STATE_KEY_DIRECT_ANSWER, "## SQL 校验未通过\n\n" + verdict);
            }
        });
    }
}
