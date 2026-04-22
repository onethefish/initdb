/*
 * Copyright 2026-2027 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.fish.initDB.config;

import cn.fish.initDB.tool.impl.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@AutoConfigureAfter(RepositoryConfig.class)
public class DBAgentConfiguration {

    private static final String DESCRIPTION = "数据库智能助手，支持查询表结构、执行SQL查询、分析数据功能";

    private static final String SYSTEM_PROMPT = """
            你是中文数据库助手。
           
            规则：
            1. 用户提出无关数据库操作的问题时，请正常回答同时引导用户尽量问数据库相关的问题
            2. 仅执行SELECT查询，禁止DML操作
            3. 默认限制10条结果，除非用户指定
            4. 表无数据时明确告知，勿重复查询
            5. 每个工具在一次对话中最多调用一次
           
           响应策略：
            - 用户问"有哪些表/列出表/列出所有表" → 调用get_all_tables后直接返回结果，不要继续其他步骤
            - 用户问"表结构/字段信息" → 调用get_table_schema后直接返回结果
            - 用户要查具体数据 → 按工作流程执行
           
            查数据完整流程（仅在用户明确要求查数据时执行）：
            1. get_all_tables - 获取所有表
            2. get_table_schema - 获取相关表结构
            3. 编写SQL
            4. sql_check - 验证SQL
            5. get_table_data - 执行查询
            6. 用中文回答
           
            注意：
            - 简单查询（只问表名/表结构）不需要走完整流程
            - 执行前必验证SQL
           """;


    private final ChatModel chatModel;
    private final GetAllTablesTool getAllTablesTool;
    private final GetTableSchemaTool getTableSchemaTool;
    private final QuerySqlCheckTool querySqlCheckTool;
    private final GetTableDataTool getTableDataTool;
    private final KnowledgeRetrievalTool knowledgeRetrievalTool;
    private final MemorySaver memorySaver;

    public DBAgentConfiguration(ChatModel chatModel, GetAllTablesTool getAllTablesTool, GetTableSchemaTool getTableSchemaTool,
                                QuerySqlCheckTool querySqlCheckTool, GetTableDataTool getTableDataTool,
                                KnowledgeRetrievalTool knowledgeRetrievalTool, MemorySaver memorySaver) {
        this.chatModel = chatModel;
        this.getAllTablesTool = getAllTablesTool;
        this.getTableSchemaTool = getTableSchemaTool;
        this.querySqlCheckTool = querySqlCheckTool;
        this.getTableDataTool = getTableDataTool;
        this.knowledgeRetrievalTool = knowledgeRetrievalTool;
        this.memorySaver = memorySaver;
    }


    @Bean
    public ReactAgent reactAgent() {

        return ReactAgent.builder()
                         .name("数据库智能体")          // 名称
                         .systemPrompt(SYSTEM_PROMPT) //提示词
                         .description(DESCRIPTION) //描述
                         .model(chatModel)
                         .saver(memorySaver)
                         .maxParallelTools(2)
                         .enableLogging(true)
                         // 设置工具
                         .tools(getAllTablesTool.toolCallback()
                                 , getTableSchemaTool.toolCallback()
                                 , querySqlCheckTool.toolCallback()
                                 , getTableDataTool.toolCallback()
                                 //                                 , knowledgeRetrievalTool.toolCallback()
                         )
                         .build();
    }

    @Bean
    public ChatClient chatClient() {
        MessageWindowChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
                                                                                 .maxMessages(10)
                                                                                 .build();
        return ChatClient.builder(chatModel)
                         .defaultAdvisors(MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build())
                         .build();
    }


}
