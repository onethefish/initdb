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
package cn.fish.initDB.tool.impl;

import cn.fish.initDB.entity.Table;
import cn.fish.initDB.repository.DataBaseRepository;
import cn.fish.initDB.tool.AgentAbstractTool;
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
import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 查询表结构的工具
 */
@Slf4j
@Component
public class GetTableSchemaTool extends AgentAbstractTool implements BiFunction<GetTableSchemaTool.Request, ToolContext, String> {

    private final DataBaseRepository dataBaseRepository;

    public GetTableSchemaTool(DataBaseRepository dataBaseRepository) {
        this.dataBaseRepository = dataBaseRepository;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("GetTableSchemaTool::apply");
        String sessionId = getSessionId(toolContext);
        List<String> tableNames = Arrays.stream(request.tables().split(","))
                                        .map(String::trim).
                                        filter(trim -> !trim.isEmpty()).
                                        map(this::sanitizeTableName)
                                        .toList();
        if (tableNames.isEmpty()) {
            return "No table names provided. Please specify table names separated by commas.";
        }
        List<Table> tables = new ArrayList<>();
        for (String tableName : tableNames) {
            Table table = dataBaseRepository.queryTableSchema(sessionId, tableName);
            if (table != null) {
                tables.add(table);
            }
        }
        String result = JSON.toJSONString(tables, JSONWriter.Feature.IgnoreEmpty);
        log.info("Found {} tables: {}", tables.size(), result);
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
        String description = "获取数据库中表的详细信息。在返回给用户信息前，使用此工具来获取具体表的详细信息。此工具的返回结果是JSON格式的表详细信息，输入应为逗号分隔的表名列表。例如：'user,order,products'";
        return FunctionToolCallback.builder("get_table_schema", this)
                                   .description(description)
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("获取指定表结构的请求")
    public static final class Request {
        @JsonProperty(value = "tables", required = true)
        @JsonPropertyDescription("要获取结构的表名列表，使用逗号分隔。例如：'users, orders, products'")
        private final String tables;

        public Request(@JsonProperty(value = "tables", required = true)
                       @JsonPropertyDescription("要获取结构的表名列表，使用逗号分隔。例如：'users, orders, products'") String tables) {
            this.tables = tables;
        }

        @JsonProperty(value = "tables", required = true)
        @JsonPropertyDescription("要获取结构的表名列表，使用逗号分隔。例如：'users, orders, products'")
        public String tables() {
            return tables;
        }
    }

}
