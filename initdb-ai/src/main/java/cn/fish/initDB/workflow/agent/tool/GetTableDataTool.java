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

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.database.service.DataBaseService;
import cn.fish.database.sql.SelectSqlRowLimiter;
import cn.fish.database.sql.SqlDialectResolver;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
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

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 500;

    private final DataBaseService dataBaseService;
    private final ChatSessionRepository chatSessionRepository;
    private final AgentDatasourceRepository agentDatasourceRepository;

    public GetTableDataTool(
            DataBaseService dataBaseService,
            ChatSessionRepository chatSessionRepository,
            AgentDatasourceRepository agentDatasourceRepository) {
        this.dataBaseService = dataBaseService;
        this.chatSessionRepository = chatSessionRepository;
        this.agentDatasourceRepository = agentDatasourceRepository;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("GetTableDataTool::apply");
        String sessionId = getSessionId(toolContext);
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        int rowLimit = resolveRowLimit(request.limit());
        String sql = SelectSqlRowLimiter.ensureSelectRowLimit(
                request.query(), rowLimit, SqlDialectResolver.fromChatSession(chatSession, agentDatasourceRepository));
        log.info("sql:{}", sql);
        List<Map<String, Object>> maps = dataBaseService.queryTableData(chatSession, sql);
        String result = JSON.toJSONString(maps);
        log.info("result:{}", result);
        return result;

    }

    private static int resolveRowLimit(Integer requested) {
        if (requested == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, Math.max(1, requested));
    }

    public ToolCallback toolCallback() {
        String description = "执行数据库的SQL SELECT查询并返回结果，返回的查询结果为JSON对象格式。" +
                "如果输出为 [] 表示这张表没数据，请不要重复调用。" +
                "重要提示：出于安全考虑，仅允许SELECT查询。DML语句（INSERT, UPDATE, DELETE, DROP）将被拒绝。" +
                "在执行前，请始终使用check_query来验证您的查询。" +
                "若 SQL 中未包含 LIMIT，默认追加 LIMIT " + DEFAULT_LIMIT + "；需要更多行时可在参数 limit 中指定（1–" + MAX_LIMIT + "）。";
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

        @JsonProperty(value = "limit", required = false)
        @JsonPropertyDescription("返回的最大行数（可选）。省略时默认 " + DEFAULT_LIMIT + " 行；需要更多数据时传入正整数，最大 " + MAX_LIMIT + "。若 SQL 已含 LIMIT 则忽略此参数。")
        private final Integer limit;

        public Request(@JsonProperty(value = "query", required = true)
                       @JsonPropertyDescription("要执行的SQL SELECT查询语句。只允许SELECT语句。") String query,
                       @JsonProperty(value = "limit", required = false)
                       @JsonPropertyDescription("返回的最大行数（可选）。省略时默认 " + DEFAULT_LIMIT + " 行；需要更多数据时传入正整数，最大 " + MAX_LIMIT + "。若 SQL 已含 LIMIT 则忽略此参数。") Integer limit) {
            this.query = query;
            this.limit = limit;
        }

        @JsonProperty(value = "query", required = true)
        @JsonPropertyDescription("要执行的SQL SELECT查询语句。只允许SELECT语句。")
        public String query() {
            return query;
        }

        @JsonProperty(value = "limit", required = false)
        @JsonPropertyDescription("返回的最大行数（可选）。省略时默认 " + DEFAULT_LIMIT + " 行；需要更多数据时传入正整数，最大 " + MAX_LIMIT + "。若 SQL 已含 LIMIT 则忽略此参数。")
        public Integer limit() {
            return limit;
        }

    }

}
