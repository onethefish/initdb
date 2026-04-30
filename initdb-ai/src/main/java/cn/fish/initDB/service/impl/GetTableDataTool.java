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
package cn.fish.initDB.service.impl;

import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.database.repository.DataBaseRepository;
import cn.fish.chart.entity.ChatSession;
import cn.fish.initDB.service.AgentAbstractTool;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * 查询表结数据的工具
 */
@Slf4j
@Component
public class GetTableDataTool extends AgentAbstractTool implements BiFunction<GetTableDataTool.Request, ToolContext, String> {

    private final DataBaseRepository dataBaseRepository;
    private final ChatSessionRepository chatSessionRepository;
    @Value("${database-agent.max-results:10}")
    private int maxResults;

    public GetTableDataTool(DataBaseRepository dataBaseRepository, ChatSessionRepository chatSessionRepository) {
        this.dataBaseRepository = dataBaseRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("GetTableDataTool::apply");
        String sessionId = getSessionId(toolContext);
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        String sql = addLimitIfNeeded(request.query);
        log.info("sql:{}", sql);
        List<Map<String, Object>> maps = dataBaseRepository.queryTableData(chatSession, sql);
        String result = JSON.toJSONString(maps);
        log.info("result:{}", result);
        return result;

    }

    // 安全兜底
    private String addLimitIfNeeded(String query) {
        String lowerQuery = query.toLowerCase();
        if (!lowerQuery.contains(" limit ") && !lowerQuery.contains("\nlimit ")) {
            // Remove trailing semicolon if present
            if (query.endsWith(";")) {
                query = query.substring(0, query.length() - 1);
            }
            return query + " LIMIT " + maxResults;
        }
        return query;
    }

    public ToolCallback toolCallback() {
        String description = "执行数据库的SQL SELECT查询并返回结果，返回的查询结果为JSON对象格式。" +
                "如果输出为 [] 表示这张表没数据，请不要重复调用。" +
                "重要提示：出于安全考虑，仅允许SELECT查询。DML语句（INSERT, UPDATE, DELETE, DROP）将被拒绝。" +
                "在执行前，请始终使用check_query来验证您的查询。默认情况下，结果限制为 " + maxResults + " 行";
        return FunctionToolCallback.builder("get_table_data", this)
                                   .description(description)
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("执行SQL查询的请求")
    public static final class Request {
        @JsonProperty(value = "query", required = true)
        @JsonPropertyDescription("要执行的SQL SELECT查询语句。只允许SELECT语句。")
        private final String query;

        public Request(@JsonProperty(value = "query", required = true)
                       @JsonPropertyDescription("要执行的SQL SELECT查询语句。只允许SELECT语句。") String query) {
            this.query = query;
        }

        @JsonProperty(value = "query", required = true)
        @JsonPropertyDescription("要执行的SQL SELECT查询语句。只允许SELECT语句。")
        public String query() {
            return query;
        }

    }

}
