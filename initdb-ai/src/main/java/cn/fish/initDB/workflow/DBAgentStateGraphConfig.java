package cn.fish.initDB.workflow;

import cn.fish.initDB.constants.InitDBConstants;
import cn.hutool.core.util.ObjectUtil;
import cn.fish.initDB.service.ContextualizeService;
import cn.fish.initDB.workflow.node.DbAgentInputBridgeNode;
import cn.fish.initDB.workflow.node.DbDirectExecuteQueryNode;
import cn.fish.initDB.workflow.node.DbDirectNl2SqlNode;
import cn.fish.initDB.workflow.node.DbDirectSqlGuardNode;
import cn.fish.initDB.workflow.node.DbDirectTableCatalogNode;
import cn.fish.initDB.workflow.node.DbIntentClassificationNode;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * DB Agent 聊天工作流：路由 → 用户明确写出 SELECT/WITH（或整段单个 ```sql 围栏）时直连；否则由意图节点中的模型在「单表明细直连」与「ReAct 对话」间分类，再走 表清单→NL2SQL→校验 或 桥接→ReAct。
 * 问句补全见 {@link ContextualizeService}（在 {@link cn.fish.initDB.service.impl.DBAgentServiceImpl} 中图外执行）。
 */
@Slf4j
@Configuration
public class DBAgentStateGraphConfig {

    @Bean(name = InitDBConstants.DB_CHAT_WORKFLOW_BEAN)
    public CompiledGraph dbChatWorkflowGraph(
            @Qualifier(InitDBConstants.DB_REACT_AGENT_BEAN) ReactAgent dbReactAgent,
            MemorySaver memorySaver,
            DbIntentClassificationNode dbIntentClassificationNode,
            DbDirectTableCatalogNode dbDirectTableCatalogNode,
            DbDirectNl2SqlNode dbDirectNl2SqlNode,
            DbDirectSqlGuardNode dbDirectSqlGuardNode,
            DbDirectExecuteQueryNode dbDirectExecuteQueryNode) {
        try {
            return buildDbChatWorkflow(dbReactAgent, memorySaver, dbIntentClassificationNode, dbDirectTableCatalogNode,
                    dbDirectNl2SqlNode, dbDirectSqlGuardNode, dbDirectExecuteQueryNode);
        } catch (GraphStateException e) {
            throw new IllegalStateException("db_chat_workflow compile failed", e);
        }
    }

    private static CompiledGraph buildDbChatWorkflow(
            ReactAgent dbReactAgent,
            MemorySaver memorySaver,
            DbIntentClassificationNode intentClassificationNode,
            DbDirectTableCatalogNode directTableCatalogNode,
            DbDirectNl2SqlNode directNl2SqlNode,
            DbDirectSqlGuardNode directSqlGuardNode,
            DbDirectExecuteQueryNode directExecuteQueryNode) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> m = new HashMap<>();
            m.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            m.put(InitDBConstants.STATE_KEY_MESSAGES, new AppendStrategy());
            m.put(InitDBConstants.STANDALONE, new ReplaceStrategy());
            m.put(InitDBConstants.STATE_KEY_DB_BUNDLE, new ReplaceStrategy());
            m.put(InitDBConstants.STATE_KEY_DIRECT_EXECUTE_STREAM, new ReplaceStrategy());
            return m;
        };
        StateGraph stateGraph = new StateGraph(InitDBConstants.GRAPH_NAME, keyStrategyFactory, StateGraph.DEFAULT_JACKSON_SERIALIZER);
        stateGraph.addNode(InitDBConstants.NODE_DB_INTENT, node_async(intentClassificationNode));
        stateGraph.addNode(InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE, node_async(new DbAgentInputBridgeNode()));
        stateGraph.addNode(InitDBConstants.NODE_DB_REACT, dbReactAgent.getAndCompileGraph());
        stateGraph.addNode(InitDBConstants.NODE_DB_DIRECT_TABLE_CATALOG, node_async(directTableCatalogNode));
        stateGraph.addNode(InitDBConstants.NODE_DB_DIRECT_NL2SQL, node_async(directNl2SqlNode));
        stateGraph.addNode(InitDBConstants.NODE_DB_DIRECT_SQL_GUARD, node_async(directSqlGuardNode));
        stateGraph.addNode(InitDBConstants.NODE_DB_DIRECT_EXECUTE, node_async(directExecuteQueryNode));

        stateGraph.addEdge(StateGraph.START, InitDBConstants.NODE_DB_INTENT);
        stateGraph.addConditionalEdges(
                InitDBConstants.NODE_DB_INTENT,
                edge_async(DBAgentStateGraphConfig::intentRouteTarget),
                Map.of(
                        InitDBConstants.ROUTE_REACT_VALUE, InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE,
                        InitDBConstants.ROUTE_DIRECT_DATA_VALUE, InitDBConstants.NODE_DB_DIRECT_TABLE_CATALOG
                ));

        stateGraph.addEdge(InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE, InitDBConstants.NODE_DB_REACT);
        stateGraph.addEdge(InitDBConstants.NODE_DB_REACT, StateGraph.END);

        stateGraph.addConditionalEdges(
                InitDBConstants.NODE_DB_DIRECT_TABLE_CATALOG,
                edge_async(DBAgentStateGraphConfig::directCatalogBranch),
                Map.of(
                        InitDBConstants.DIRECT_CATALOG_EDGE_OK, InitDBConstants.NODE_DB_DIRECT_NL2SQL,
                        InitDBConstants.DIRECT_CATALOG_EDGE_FAIL, StateGraph.END
                ));
        stateGraph.addEdge(InitDBConstants.NODE_DB_DIRECT_NL2SQL, InitDBConstants.NODE_DB_DIRECT_SQL_GUARD);
        stateGraph.addConditionalEdges(
                InitDBConstants.NODE_DB_DIRECT_SQL_GUARD,
                edge_async(DBAgentStateGraphConfig::sqlGuardBranch),
                Map.of(
                        InitDBConstants.SQL_GUARD_EDGE_OK, InitDBConstants.NODE_DB_DIRECT_EXECUTE,
                        InitDBConstants.SQL_GUARD_EDGE_FAIL, StateGraph.END
                ));
        stateGraph.addEdge(InitDBConstants.NODE_DB_DIRECT_EXECUTE, StateGraph.END);
        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML, "workflow graph");

        log.info("workflow in PlantUML format as follows \n{}", graphRepresentation.content());

        CompileConfig compileConfig = CompileConfig.builder()
                                                   .saverConfig(SaverConfig.builder().register(memorySaver).build())
                                                   .recursionLimit(InitDBConstants.DB_CHAT_WORKFLOW_RECURSION_LIMIT)
                                                   .build();
        return stateGraph.compile(compileConfig);
    }

    /**
     * 条件边必须命中 {@link Map} 的 key；checkpoint 反序列化后类型可能非 String，统一归一化避免走错分支或映射失败。
     */
    private static String intentRouteTarget(OverAllState state) {
        Object v = DbWorkflowBundle.readCopy(state).getOrDefault(InitDBConstants.STATE_KEY_DB_ROUTE, InitDBConstants.ROUTE_REACT_VALUE);
        if (ObjectUtil.isNull(v)) {
            return InitDBConstants.ROUTE_REACT_VALUE;
        }
        String s = String.valueOf(v).trim();
        if (InitDBConstants.ROUTE_DIRECT_DATA_VALUE.equals(s)) {
            return InitDBConstants.ROUTE_DIRECT_DATA_VALUE;
        }
        return InitDBConstants.ROUTE_REACT_VALUE;
    }

    private static String sqlGuardBranch(OverAllState state) {
        Object ok = DbWorkflowBundle.readCopy(state).getOrDefault(InitDBConstants.STATE_KEY_SQL_GUARD_OK, Boolean.FALSE);
        boolean pass = Boolean.TRUE.equals(ok)
                || (ok instanceof String s && "true".equalsIgnoreCase(s.trim()));
        return pass ? InitDBConstants.SQL_GUARD_EDGE_OK : InitDBConstants.SQL_GUARD_EDGE_FAIL;
    }

    private static String directCatalogBranch(OverAllState state) {
        Object ok = DbWorkflowBundle.readCopy(state).getOrDefault(InitDBConstants.STATE_KEY_DIRECT_CATALOG_OK, Boolean.FALSE);
        boolean pass = Boolean.TRUE.equals(ok)
                || (ok instanceof String s && "true".equalsIgnoreCase(s.trim()));
        return pass ? InitDBConstants.DIRECT_CATALOG_EDGE_OK : InitDBConstants.DIRECT_CATALOG_EDGE_FAIL;
    }
}
