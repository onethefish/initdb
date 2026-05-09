package cn.fish.initDB.workflow;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.service.ContextualizeService;
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
 * DB Agent 聊天工作流：仅「桥接 → ReAct 子图」；问句补全见 {@link ContextualizeService}（在 {@link cn.fish.initDB.service.impl.DBAgentServiceImpl} 中图外执行）。
 */
@Configuration
public class DBAgentStateGraphConfig {

    @Bean(name = InitDBConstants.DB_CHAT_WORKFLOW_BEAN)
    public CompiledGraph dbChatWorkflowGraph(@Qualifier(InitDBConstants.DB_REACT_AGENT_BEAN) ReactAgent dbReactAgent, MemorySaver memorySaver) {
        try {
            return buildDbChatWorkflow(dbReactAgent, memorySaver);
        } catch (GraphStateException e) {
            throw new IllegalStateException("db_chat_workflow compile failed", e);
        }
    }

    private static CompiledGraph buildDbChatWorkflow(ReactAgent dbReactAgent, MemorySaver memorySaver) throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> m = new HashMap<>();
            m.put(OverAllState.DEFAULT_INPUT_KEY, new ReplaceStrategy());
            m.put(InitDBConstants.STATE_KEY_MESSAGES, new AppendStrategy());
            m.put(InitDBConstants.STANDALONE, new ReplaceStrategy());
            return m;
        };
        StateGraph graph = new StateGraph(InitDBConstants.GRAPH_NAME_DB_CHAT_WORKFLOW, keyStrategyFactory, StateGraph.DEFAULT_JACKSON_SERIALIZER);
        graph.addNode(InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE, node_async(new DbAgentInputBridgeNode()));
        graph.addNode(InitDBConstants.NODE_DB_REACT, dbReactAgent.getAndCompileGraph());
        graph.addEdge(StateGraph.START, InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE);
        graph.addEdge(InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE, InitDBConstants.NODE_DB_REACT);
        graph.addEdge(InitDBConstants.NODE_DB_REACT, StateGraph.END);
        CompileConfig compileConfig = CompileConfig.builder()
                                                   .saverConfig(SaverConfig.builder().register(memorySaver).build())
                                                   .recursionLimit(InitDBConstants.DB_CHAT_WORKFLOW_RECURSION_LIMIT)
                                                   .build();
        return graph.compile(compileConfig);
    }
}
