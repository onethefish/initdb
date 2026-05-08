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
package cn.fish.initDB.workflow.tool.impl;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;


@Slf4j
@Component
public class QuerySqlCheckTool implements BiFunction<QuerySqlCheckTool.Request, ToolContext, String> {


    private static final String CHECK_PROMPT_TEMPLATE = """
            你是一个SQL查询验证器。请检查以下SQL查询语句查询是否存在常见错误：
            
            ```sql
            %s
            ```
            
            检查项目：
            1. 语法错误
            2. 错误的列名或表名（如果提供了上下文）
            3. 字符串值缺少引号
            4. JOIN条件不正确
            5. GROUP BY子句问题
            6. 任何潜在的SQL注入漏洞
            
            如果查询看起来正确，请准确回复：
            "校验成功：该查询语句看起来没问题"
            
            如果存在问题，请回复：
            "校验失败:" 后跟问题编号列表及修复建议。
            
            请保持回复简洁。
            """;
    private final ChatModel chatModel;

    public QuerySqlCheckTool(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("QuerySqlCheckTool::apply");
        log.info("Query to check: {}", request.query());

        try {
            String promptText = String.format(CHECK_PROMPT_TEMPLATE, request.query());
            Prompt prompt = new Prompt(promptText);
            String result = chatModel.call(prompt).getResult().getOutput().getText();

            log.info("Check result: {}", result);
            return result;
        } catch (Exception e) {
            log.error("Error checking query", e);
            return "Error checking query: " + e.getMessage();
        }
    }

    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("sql_check", this)
                                   .description("在执行前验证SQL查询的常见错误。在使用get_table_data运行查询之前，请使用此工具再次检查您的查询。该工具将识别语法错误、潜在问题并提出修复建议。")
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("请求检查SQL语句查询中的错误")
    public record Request(@JsonProperty(value = "query", required = true)
                          @JsonPropertyDescription("用于验证常见错误的查询SQL基于") String query) {
    }

}
