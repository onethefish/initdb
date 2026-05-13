package cn.fish.initDB.workflow.agent;

import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.initDB.workflow.agent.tool.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
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

    public static final String REACT_AGENT_DESCRIPTION =
            "数据库智能助手：以数据分析与多步查库为主（统计、对比、关联、归因等），兼表结构、只读 SQL 与知识库检索；单表明细拉取由系统直连优先，本代理处理其余库内任务。";

    @Bean(name = REACT_AGENT_BEAN)
    public ReactAgent dbReactAgent(ChatModel chatModel, GetAllTablesTool getAllTablesTool,
                                   GetTableSchemaTool getTableSchemaTool, QuerySqlCheckTool querySqlCheckTool,
                                   GetTableDataTool getTableDataTool, KnowledgeRetrievalTool knowledgeRetrievalTool,
                                   BaseCheckpointSaver checkpointSaver,
                                   ApplicationPromptTemplates applicationPromptTemplates) {
        return ReactAgent.builder()
                         .name(REACT_AGENT_DISPLAY_NAME)
                         .systemPrompt(applicationPromptTemplates.dbReactSystemText())
                         .description(REACT_AGENT_DESCRIPTION)
                         .model(chatModel)
                         .saver(checkpointSaver)
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
