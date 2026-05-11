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
import cn.fish.initDB.entity.Table;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 查询表结构的工具
 */
@Slf4j
@Component
public class GetTableSchemaTool extends AgentAbstractTool implements BiFunction<GetTableSchemaTool.Request, ToolContext, String> {

    private final DataBaseService dataBaseService;
    private final ChatSessionRepository chatSessionRepository;

    public GetTableSchemaTool(DataBaseService dataBaseService, ChatSessionRepository chatSessionRepository) {
        this.dataBaseService = dataBaseService;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("GetTableSchemaTool::apply");
        String sessionId = getSessionId(toolContext);
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        List<String> tableNames = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (String part : request.tables().split(",")) {
            String raw = part.trim();
            if (raw.isEmpty()) {
                continue;
            }
            try {
                tableNames.add(sanitizeTableName(raw));
            } catch (IllegalArgumentException ex) {
                log.warn("get_table_schema rejected non-physical name: {}", raw);
                rejected.add(raw);
            }
        }
        if (!rejected.isEmpty() && tableNames.isEmpty()) {
            return "以下不是合法物理表名（仅允许字母、数字、下划线，且须与 get_all_tables 返回的 tableName 字段一致；中文说明 remarks 不能作为表名传入）："
                    + String.join("、", rejected)
                    + "。请先在 get_all_tables 的 JSON 中按 remarks 找到用户所指表，再仅使用对应 tableName 调用本工具。";
        }
        if (tableNames.isEmpty()) {
            return "No table names provided. Please specify table names separated by commas.";
        }
        List<Table> tables = new ArrayList<>();
        for (String tableName : tableNames) {
            Table table = dataBaseService.queryTableSchema(chatSession, tableName);
            if (ObjectUtil.isNotNull(table)) {
                tables.add(table);
            }
        }
        String result = JSON.toJSONString(tables, JSONWriter.Feature.IgnoreEmpty);
        log.info("Found {} tables: {}", tables.size(), result);
        if (!rejected.isEmpty()) {
            result = result + "\n\n【提示】以下输入已忽略（请改用 get_all_tables 中的 tableName，勿用中文 remarks 作表名）：" + String.join("、", rejected);
        }
        return result;

    }


    private String sanitizeTableName(String tableName) {
        // Basic SQL injection prevention - only allow alphanumeric and underscore
        if (!tableName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid table name: " + tableName);
        }
        return tableName;
    }

    public ToolCallback toolCallback() {
        String description = "获取数据库中表的详细信息。输入 tables 须为 get_all_tables 返回的 JSON 里每个对象的 tableName（物理表名），逗号分隔，例如 agent,chat_session。remarks 中的中文说明不能作为本参数传入。";
        return FunctionToolCallback.builder("get_table_schema", this)
                                   .description(description)
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("获取指定表结构的请求")
    public static final class Request {
        @JsonProperty(value = "tables", required = true)
        @JsonPropertyDescription("仅物理表名：get_all_tables 返回的 tableName，逗号分隔，如 agent,chat_session。禁止传入中文 remarks 当表名。")
        private final String tables;

        public Request(@JsonProperty(value = "tables", required = true)
                       @JsonPropertyDescription("仅物理表名：get_all_tables 的 tableName，逗号分隔。禁止中文说明。") String tables) {
            this.tables = tables;
        }

        @JsonProperty(value = "tables", required = true)
        @JsonPropertyDescription("get_all_tables 的 tableName，逗号分隔。禁止中文 remarks。")
        public String tables() {
            return tables;
        }
    }

}
