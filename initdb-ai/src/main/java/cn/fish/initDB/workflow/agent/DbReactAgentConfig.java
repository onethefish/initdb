package cn.fish.initDB.workflow.agent;

import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.constants.InitDBConstants;
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

    @Bean(name = InitDBConstants.DB_REACT_AGENT_BEAN)
    public ReactAgent dbReactAgent(ChatModel chatModel, GetAllTablesTool getAllTablesTool,
                                   GetTableSchemaTool getTableSchemaTool, QuerySqlCheckTool querySqlCheckTool,
                                   GetTableDataTool getTableDataTool, KnowledgeRetrievalTool knowledgeRetrievalTool,
                                   MemorySaver memorySaver, ApplicationPromptTemplates applicationPromptTemplates) {
        return ReactAgent.builder()
                         .name(InitDBConstants.DB_REACT_AGENT_DISPLAY_NAME)
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
