package cn.fish.initDB.service.impl;

import cn.fish.initDB.workflow.agent.tool.QuerySqlCheckTool;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * 与 {@link cn.fish.initDB.workflow.node.DbDirectSqlGuardNode} 使用同一套 {@link QuerySqlCheckTool} 校验逻辑。
 */
@Service
public class ExportSqlGuardService {

    private static final ToolContext EMPTY_TOOL_CONTEXT = new ToolContext(new HashMap<>());

    private final QuerySqlCheckTool querySqlCheckTool;

    public ExportSqlGuardService(QuerySqlCheckTool querySqlCheckTool) {
        this.querySqlCheckTool = querySqlCheckTool;
    }

    /**
     * @return 校验通过为 true（与直连节点一致：结果串包含「校验成功」）
     */
    public boolean isAllowed(String sql) {
        if (StrUtil.isBlank(sql)) {
            return false;
        }
        String verdict = querySqlCheckTool.apply(new QuerySqlCheckTool.Request(sql.trim()), EMPTY_TOOL_CONTEXT);
        return ObjectUtil.isNotNull(verdict) && verdict.contains("校验成功");
    }

    /** 原始校验输出，便于向用户展示失败原因 */
    public String checkVerdict(String sql) {
        if (StrUtil.isBlank(sql)) {
            return "SQL 为空。";
        }
        return querySqlCheckTool.apply(new QuerySqlCheckTool.Request(sql.trim()), EMPTY_TOOL_CONTEXT);
    }
}
