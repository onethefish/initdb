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

import cn.fish.initDB.tool.ShowAllTablesTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class SqlAgentConfiguration {

    private static final String SYSTEM_PROMPT = """
            You are an agent designed to interact with a SQL database.
            Given an input question, create a syntactically correct SQLite query to run,
            then look at the results of the query and return the answer.
            
            Unless the user specifies a specific number of examples they wish to obtain,
            always limit your query to at most 10 results.
            
            You can order the results by a relevant column to return the most interesting
            examples in the database. Never query for all the columns from a specific table,
            only ask for the relevant columns given the question.
            
            You MUST double check your query before executing it. If you get an error while
            executing a query, rewrite the query and try again.
            
            DO NOT make any DML statements (INSERT, UPDATE, DELETE, DROP etc.) to the database.
            Only SELECT queries are allowed.
            
            To start you should ALWAYS look at the tables in the database to see what you
            can query. Do NOT skip this step.
            
            Then you should query the schema of the most relevant tables to understand their structure.
            
            After getting the schema, use check_query to validate your SQL before executing.
            
            Finally, execute the query and provide a clear, natural language answer based on the results.
            
            Remember to:
            1. First call list_tables to see available tables
            2. Then call get_schema for relevant tables
            3. Then call check_query to validate your SQL
            4. Finally call execute_query to get results
            5. Synthesize the results into a helpful answer
            """;

    private final ChatModel chatModel;
    private final ShowAllTablesTool showAllTablesTool;

    public SqlAgentConfiguration(ChatModel chatModel, ShowAllTablesTool showAllTablesTool) {
        this.chatModel = chatModel;
        this.showAllTablesTool = showAllTablesTool;
    }

    @Bean
    public ReactAgent sqlAgent() {
        return ReactAgent.builder()
                         .name("database-agent")          // 智能体
                         .description(SYSTEM_PROMPT) //智能体的描述或系统提示词
                         .model(chatModel)
                         .saver(new MemorySaver())
                         // 设置工具
                         .tools(showAllTablesTool.toolCallback()
                                 //                    , getSchemaTool.toolCallback()
                         )
                         .build();
    }

}
