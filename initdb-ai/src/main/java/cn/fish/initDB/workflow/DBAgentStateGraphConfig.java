package cn.fish.initDB.workflow;

import cn.fish.initDB.workflow.agent.DbReactAgentConfig;
import cn.fish.initDB.constants.DbChatInputConstants;
import cn.fish.initDB.workflow.node.DbAgentInputBridgeNode;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * DB Agent 聊天工作流：仅「桥接 → ReAct 子图」；问句补全见 {@link cn.fish.initDB.service.QuestionContextualizeService}（在 {@link cn.fish.initDB.service.impl.DBAgentServiceImpl} 中图外执行）。
 */
@Configuration
public class DBAgentStateGraphConfig {

    public static final String DB_CHAT_WORKFLOW_BEAN = "dbChatWorkflowGraph";

    private static final String NODE_DB_REACT = "db_react";

    @Bean(name = DB_CHAT_WORKFLOW_BEAN)
    public CompiledGraph dbChatWorkflowGraph(
            @Qualifier(DbReactAgentConfig.DB_REACT_AGENT_BEAN) ReactAgent dbReactAgent,
            MemorySaver memorySaver) {
        try {
            return buildDbChatWorkflow(dbReactAgent, memorySaver);
        } catch (GraphStateException e) {
            throw new IllegalStateException("db_chat_workflow compile failed", e);
        }
    }

    private static CompiledGraph buildDbChatWorkflow(ReactAgent dbReactAgent, MemorySaver memorySaver)
            throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> m = new HashMap<>();
            m.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            m.put("messages", new AppendStrategy());
            m.put(DbChatInputConstants.STANDALONE, new ReplaceStrategy());
            return m;
        };
        StateGraph graph = new StateGraph("db_chat_workflow", keyStrategyFactory, StateGraph.DEFAULT_JACKSON_SERIALIZER);
        graph.addNode(DbAgentInputBridgeNode.NODE_ID, node_async(new DbAgentInputBridgeNode()));
        graph.addNode(NODE_DB_REACT, dbReactAgent.getAndCompileGraph());
        graph.addEdge(StateGraph.START, DbAgentInputBridgeNode.NODE_ID);
        graph.addEdge(DbAgentInputBridgeNode.NODE_ID, NODE_DB_REACT);
        graph.addEdge(NODE_DB_REACT, StateGraph.END);
        CompileConfig compileConfig = CompileConfig.builder()
                                                   .saverConfig(SaverConfig.builder().register(memorySaver).build())
                                                   .recursionLimit(100)
                                                   .build();
        return graph.compile(compileConfig);
    }
}
