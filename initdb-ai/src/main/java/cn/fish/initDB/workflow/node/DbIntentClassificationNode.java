package cn.fish.initDB.workflow.node;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.util.ExplicitSqlUserInput;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 路由：仅当用户<strong>明确写出</strong>可执行的查询 SQL（SELECT / WITH）时才走直连执行链路；
 * 自然语言问题一律走 ReAct，以便通过表结构等工具解析表名。
 */
@Slf4j
@Component
public class DbIntentClassificationNode implements NodeAction {

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String standalone = state.value(InitDBConstants.STANDALONE, "");
        String route = classifyRoute(standalone);
        log.debug("db intent route={} for question snippet: {}", route, abbreviate(standalone));
        return DbWorkflowBundle.writeBundle(state, b -> b.put(InitDBConstants.STATE_KEY_DB_ROUTE, route));
    }

    private static String classifyRoute(String standalone) {
        if (!StringUtils.hasText(standalone)) {
            return InitDBConstants.ROUTE_REACT_VALUE;
        }
        if (ExplicitSqlUserInput.matches(standalone)) {
            return InitDBConstants.ROUTE_DIRECT_DATA_VALUE;
        }
        return InitDBConstants.ROUTE_REACT_VALUE;
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 120 ? s : s.substring(0, 120) + "...";
    }
}
