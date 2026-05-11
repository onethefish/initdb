package cn.fish.initDB.workflow;

import cn.fish.initDB.constants.WorkflowConstants;
import cn.fish.initDB.workflow.agent.DbReactAgentConfig;
import cn.fish.initDB.workflow.node.*;
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

@Slf4j
@Configuration
public class DBAgentStateGraphConfig {

    @Bean(name = WorkflowConstants.DB_CHAT_WORKFLOW_BEAN)
    public CompiledGraph dbChatWorkflowGraph(
            @Qualifier(WorkflowConstants.DB_REACT_AGENT_BEAN) ReactAgent dbReactAgent,
            MemorySaver memorySaver,
            DbIntentClassificationNode dbIntentClassificationNode,
            DbDirectTableCatalogNode dbDirectTableCatalogNode,
            DbDirectNl2SqlNode dbDirectNl2SqlNode,
            DbDirectSqlGuardNode dbDirectSqlGuardNode,
            DbDirectExecuteQueryNode dbDirectExecuteQueryNode,
            DbAgentInputBridgeNode dbAgentInputBridgeNode) {
        try {
            return buildDbChatWorkflow(dbReactAgent, memorySaver, dbIntentClassificationNode, dbDirectTableCatalogNode,
                    dbDirectNl2SqlNode, dbDirectSqlGuardNode, dbDirectExecuteQueryNode, dbAgentInputBridgeNode);
        } catch (GraphStateException e) { // compile 等构图阶段非法（未知节点、边配置错误）时抛出，此处包装便于 Spring 启动报错
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
            DbDirectExecuteQueryNode directExecuteQueryNode,
            DbAgentInputBridgeNode dbAgentInputBridgeNode) throws GraphStateException {
        // KeyStrategyFactory：为每个 state channel 指定合并策略，多节点写同一 key 时按策略覆盖或追加
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> m = new HashMap<>();
            m.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            m.put(WorkflowConstants.STATE_KEY_MESSAGES, new AppendStrategy());
            m.put(WorkflowConstants.STANDALONE, new ReplaceStrategy());
            m.put(WorkflowConstants.STATE_KEY_DB_BUNDLE, new ReplaceStrategy());
            m.put(WorkflowConstants.STATE_KEY_DIRECT_EXECUTE_STREAM, new ReplaceStrategy());
            return m;
        };
        // new StateGraph(图名, keyStrategyFactory, serializer)：图名用于调试；serializer 供 checkpoint 等序列化
        StateGraph stateGraph = new StateGraph("db_chat_workflow", keyStrategyFactory, StateGraph.DEFAULT_JACKSON_SERIALIZER);
        // addNode(id, action)：注册节点 id；须先于 addEdge/addConditionalEdges 引用。第二参可为 node_async(NodeAction) 或子图 CompiledGraph
        stateGraph.addNode(DbIntentClassificationNode.GRAPH_NODE_ID, node_async(intentClassificationNode));
        stateGraph.addNode(DbAgentInputBridgeNode.GRAPH_NODE_ID, node_async(dbAgentInputBridgeNode));
        stateGraph.addNode(DbReactAgentConfig.GRAPH_NODE_ID, dbReactAgent.getAndCompileGraph()); // ReAct 子图整图作为单节点嵌入
        stateGraph.addNode(DbDirectTableCatalogNode.GRAPH_NODE_ID, node_async(directTableCatalogNode));
        stateGraph.addNode(DbDirectNl2SqlNode.GRAPH_NODE_ID, node_async(directNl2SqlNode));
        stateGraph.addNode(DbDirectSqlGuardNode.GRAPH_NODE_ID, node_async(directSqlGuardNode));
        stateGraph.addNode(DbDirectExecuteQueryNode.GRAPH_NODE_ID, node_async(directExecuteQueryNode));

        // addEdge(from, to)：无条件边，上一节点结束必进下一节点；START/END 为框架入口/出口
        stateGraph.addEdge(StateGraph.START, DbIntentClassificationNode.GRAPH_NODE_ID);
        Map<String, String> intentRouteEdges = new HashMap<>(4); // addConditionalEdges 第三参：分支 key -> 目标节点 id
        intentRouteEdges.put(WorkflowConstants.GRAPH_BRANCH_TRUE, DbDirectTableCatalogNode.GRAPH_NODE_ID);
        intentRouteEdges.put(WorkflowConstants.GRAPH_BRANCH_FALSE, DbAgentInputBridgeNode.GRAPH_NODE_ID);
        // addConditionalEdges(源节点, edge_async(路由), pathMap)：路由返回值必须与 pathMap 的某一 key 完全一致（含大小写）
        stateGraph.addConditionalEdges(
                DbIntentClassificationNode.GRAPH_NODE_ID,
                edge_async(DBAgentStateGraphConfig::intentRouteTarget),
                intentRouteEdges);

        stateGraph.addEdge(DbAgentInputBridgeNode.GRAPH_NODE_ID, DbReactAgentConfig.GRAPH_NODE_ID); // addEdge：桥接后必进 ReAct 子图
        stateGraph.addEdge(DbReactAgentConfig.GRAPH_NODE_ID, StateGraph.END);

        Map<String, String> directCatalogEdges = new HashMap<>(4); // 表清单节点出边：ok -> NL2SQL，fail -> END
        directCatalogEdges.put(WorkflowConstants.GRAPH_BRANCH_TRUE, DbDirectNl2SqlNode.GRAPH_NODE_ID);
        directCatalogEdges.put(WorkflowConstants.GRAPH_BRANCH_FALSE, StateGraph.END); // catalog 失败直接 END，错误说明已由节点写入 state
        stateGraph.addConditionalEdges(
                DbDirectTableCatalogNode.GRAPH_NODE_ID,
                edge_async(DBAgentStateGraphConfig::directCatalogBranch),
                directCatalogEdges);
        stateGraph.addEdge(DbDirectNl2SqlNode.GRAPH_NODE_ID, DbDirectSqlGuardNode.GRAPH_NODE_ID); // NL2SQL 后必进 SQL 守卫
        Map<String, String> sqlGuardEdges = new HashMap<>(4); // 守卫出边：ok -> 执行，fail -> END
        sqlGuardEdges.put(WorkflowConstants.GRAPH_BRANCH_TRUE, DbDirectExecuteQueryNode.GRAPH_NODE_ID);
        sqlGuardEdges.put(WorkflowConstants.GRAPH_BRANCH_FALSE, StateGraph.END);
        stateGraph.addConditionalEdges(
                DbDirectSqlGuardNode.GRAPH_NODE_ID,
                edge_async(DBAgentStateGraphConfig::sqlGuardBranch),
                sqlGuardEdges);
        stateGraph.addEdge(DbDirectExecuteQueryNode.GRAPH_NODE_ID, StateGraph.END); // 直连执行完成后结束
        // getGraph：生成 PlantUML 等可读拓扑，仅调试用
        GraphRepresentation graphRepresentation = stateGraph.getGraph(GraphRepresentation.Type.PLANTUML, "workflow graph");

        log.info("workflow in PlantUML format as follows \n{}", graphRepresentation.content());

        // compile：校验节点与边，应用 SaverConfig/recursionLimit，得到可执行 CompiledGraph
        CompileConfig compileConfig = CompileConfig.builder()
                                                   .saverConfig(SaverConfig.builder().register(memorySaver).build())
                                                   .recursionLimit(100)
                                                   .build();
        return stateGraph.compile(compileConfig); // compile：生成 CompiledGraph，非法拓扑在此抛 GraphStateException
    }

    // 供 addConditionalEdges 使用：读 bundle.db_route（Boolean，旧 checkpoint 可能为字符串，见 DbWorkflowBundle#readCopy 归一）
    private static String intentRouteTarget(OverAllState state) {
        Object v = DbWorkflowBundle.readCopy(state).get(WorkflowConstants.DB_BUNDLE_KEY_ROUTE);
        return Boolean.TRUE.equals(v) ? WorkflowConstants.GRAPH_BRANCH_TRUE : WorkflowConstants.GRAPH_BRANCH_FALSE;
    }

    // 供 addConditionalEdges 使用：读 bundle.db_sql_guard（布尔语义）；pathMap key 与 GRAPH_BRANCH_* 一致
    private static String sqlGuardBranch(OverAllState state) {
        Object ok = DbWorkflowBundle.readCopy(state).getOrDefault(WorkflowConstants.DB_BUNDLE_KEY_SQL_GUARD, Boolean.FALSE);
        boolean pass = Boolean.TRUE.equals(ok)
                || (ok instanceof String s && "true".equalsIgnoreCase(s.trim()));
        return pass ? WorkflowConstants.GRAPH_BRANCH_TRUE : WorkflowConstants.GRAPH_BRANCH_FALSE;
    }

    // 供 addConditionalEdges 使用：读 bundle.db_table_catalog（布尔语义）
    private static String directCatalogBranch(OverAllState state) {
        Object ok = DbWorkflowBundle.readCopy(state).getOrDefault(WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG, Boolean.FALSE);
        boolean pass = Boolean.TRUE.equals(ok)
                || (ok instanceof String s && "true".equalsIgnoreCase(s.trim()));
        return pass ? WorkflowConstants.GRAPH_BRANCH_TRUE : WorkflowConstants.GRAPH_BRANCH_FALSE;
    }
}
