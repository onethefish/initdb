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
package cn.fish.initDB.workflow.agent.tool;

import cn.fish.common.ai.ChatModelUsageRecorder;
import cn.fish.common.prompt.ApplicationPromptTemplates;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.function.BiFunction;


@Slf4j
@Component
public class QuerySqlCheckTool implements BiFunction<QuerySqlCheckTool.Request, ToolContext, String> {

    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;
    private final ChatModelUsageRecorder chatModelUsageRecorder;

    public QuerySqlCheckTool(ChatModel chatModel, ApplicationPromptTemplates applicationPromptTemplates,
                               ChatModelUsageRecorder chatModelUsageRecorder) {
        this.chatModel = chatModel;
        this.applicationPromptTemplates = applicationPromptTemplates;
        this.chatModelUsageRecorder = chatModelUsageRecorder;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("QuerySqlCheckTool::apply");
        log.info("Query to check: {}", request.query());

        try {
            String promptText = applicationPromptTemplates.renderQuerySqlCheck(request.query());
            Prompt prompt = new Prompt(promptText);
            long t0 = System.nanoTime();
            ChatResponse cr = chatModel.call(prompt);
            chatModelUsageRecorder.record("sql_check", cr, System.nanoTime() - t0, null);
            String result = cr.getResult().getOutput().getText();

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
