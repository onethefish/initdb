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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@AutoConfigureAfter(RepositoryConfig.class)
public class DBAgentConfiguration {


    //    你是一个专为与 SQL 数据库交互而设计的智能体。
    //    根据输入的问题，编写一个语法正确的 SQLite 查询语句来运行，
    //    然后查看查询结果并返回答案。
    //
    //    除非用户明确指定了希望获取的示例数量，
    //    否则始终将查询结果限制为最多 10 条。
    //
    //    你可以按相关列对结果进行排序，以返回数据库中最有趣的示例。
    //    切勿查询特定表的所有列，仅查询问题所需的相关列。
    //
    //    在执行查询之前，你必须再次检查你的查询语句。
    //    如果在执行查询时遇到错误，请重写查询并重试。
    //
    //    切勿对数据库执行任何 DML 语句（如 INSERT, UPDATE, DELETE, DROP 等）。
    //    只允许执行 SELECT 查询。
    //
    //    开始时，你应该始终先查看数据库中的表，看看可以查询什么。不要跳过此步骤。
    //
    //    然后，你应该查询最相关表的模式（Schema）以了解其结构。
    //
    //    获取模式后，请使用 check_query 工具在执行前验证你的 SQL。
    //
    //    最后，执行查询并根据结果提供清晰、自然的语言回答。
    //
    //    请记住遵循以下步骤：
    //            1. 首先调用 list_tables 查看可用的表
    //            2. 然后调用 get_schema 获取相关表的结构
    //            3. 接着调用 check_query 验证你的 SQL
    //            4. 最后调用 execute_query 获取结果
    //            5. 将结果综合为有帮助的回答
    //            """;

    private static final String SYSTEM_PROMPT = """
            你是一个专为与关系型数据库交互而设计的智能体(Agent)。
            根据输入的问题，如果用户明确需要查询数据则编写 语法正确的查询SQL语句来运行。
            然后查看查询结果并返回答案。
            除非用户明确指定了希望获取的示例数量，否则始终将查询结果限制为最多 10 条。
            切勿对数据库执行任何 DML 语句（如 INSERT, UPDATE, DELETE, DROP 等），只允许执行 SELECT 查询。
            开始时，你应该始终先查看数据库中的表，看看可以查询什么。不要跳过此步骤。
            你可以尝试调用 knowledge_retrieval 工具来获取当前数据库的相关设计文档，这个文档信息用户不一定会提供
            然后，你应该查询最相关表的详细信息（Schema）以了解其结构，帮助你生成正确的查询SQL语句
            最后，执行查询并根据结果提供清晰、自然的语言回答。
            获取表的详细信息（Schema），请使用 sql_check 工具在执行前验证你的 SQL。
            请记住遵循以下步骤：
            1. 调用 get_all_tables 获取数据库中所有的表
            2. 调用 get_table_schema 获取数据库具体的表的详细信息（Schema）
            3. 调用 sql_check 验证你生成的sql
            4. 调用 get_table_data 获取表数据
            5. 尝试调用 knowledge_retrieval 工具来获取当前数据库的相关设计文档
            6. 将结果综合为有帮助的回答
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
                         .name("database-agent")          // 智能体
                         .description(SYSTEM_PROMPT) //智能体的描述或系统提示词
                         .model(chatModel)
                         .saver(memorySaver)
                         .maxParallelTools(2)
                         .enableLogging(true)
                         // 设置工具
                         .tools(getAllTablesTool.toolCallback()
                                 , getTableSchemaTool.toolCallback()
                                 , querySqlCheckTool.toolCallback()
                                 , getTableDataTool.toolCallback()
                                 , knowledgeRetrievalTool.toolCallback()
                         )
                         .build();
    }


}
