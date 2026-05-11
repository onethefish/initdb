package cn.fish.initDB.workflow.agent;

import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.workflow.agent.tool.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据库 ReAct {@link ReactAgent} 定义。
 */
@Configuration
public class DbReactAgentConfig {

    // StateGraph 节点 id（父图中嵌入的 ReAct 子图），勿改字符串以免破坏 checkpoint / 流式帧匹配
    public static final String GRAPH_NODE_ID = "db_react";

    public static final String REACT_AGENT_BEAN = "dbReactAgent";
    public static final String REACT_AGENT_DISPLAY_NAME = "数据库智能体";

    @Bean(name = REACT_AGENT_BEAN)
    public ReactAgent dbReactAgent(ChatModel chatModel, GetAllTablesTool getAllTablesTool,
                                   GetTableSchemaTool getTableSchemaTool, QuerySqlCheckTool querySqlCheckTool,
                                   GetTableDataTool getTableDataTool, KnowledgeRetrievalTool knowledgeRetrievalTool,
                                   MemorySaver memorySaver, ApplicationPromptTemplates applicationPromptTemplates) {
        return ReactAgent.builder()
                         .name(REACT_AGENT_DISPLAY_NAME)
                         .systemPrompt(applicationPromptTemplates.dbReactSystemText())
                         .description(applicationPromptTemplates.dbReactDescriptionText())
                         .model(chatModel)
                         .saver(memorySaver)
                         .parallelToolExecution(false)
                         .maxParallelTools(1)
                         .enableLogging(true)
                         .tools(getAllTablesTool.toolCallback(),
                                 getTableSchemaTool.toolCallback(),
                                 querySqlCheckTool.toolCallback(),
                                 getTableDataTool.toolCallback(),
                                 knowledgeRetrievalTool.toolCallback())
                         .build();
    }
}
