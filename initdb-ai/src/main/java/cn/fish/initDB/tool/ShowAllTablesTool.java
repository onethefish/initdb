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
package cn.fish.initDB.tool;

import cn.fish.initDB.entity.Table;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 查询表结构的工具
 */
@Slf4j
@Component
public class ShowAllTablesTool implements BiFunction<ShowAllTablesTool.Request, ToolContext, String> {


    private final DataSource dataSource;

    public ShowAllTablesTool(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("ListTablesTool::apply");
        log.info("request={}", request);
        log.info("toolContext={}", toolContext);
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();//目录名称，一般都为空
            //schema = "%";//数据库名，对于mysql来说用通配符
            DatabaseMetaData dbmd = conn.getMetaData();
            String schema = dbmd.getUserName();//数据库名称
            // 表第一个字段为表名，第二个为表注释
            ResultSet tablesResultSet = dbmd.getTables(catalog, schema, "%", new String[]{"TABLE"});
            List<Table> tables = new ArrayList<>();
            while (tablesResultSet.next()) {
                Table table = new Table();
                String table_name = tablesResultSet.getString("TABLE_NAME");  //表名
                String remarks = tablesResultSet.getString("REMARKS");       //表注释 不一定有
                table.setTableName(table_name);
                table.setRemarks(remarks);
                tables.add(table);
            }
            if (tables.isEmpty()) {
                log.info("No tables found in the database");
                return "No tables found in the database.";
            }
            String result = JSON.toJSONString(tables, JSONWriter.Feature.IgnoreEmpty);
            //            String result = tables.stream()
            //                                 .map(Table::getTableName)
            //                                 .collect(Collectors.joining(",")); // 3. 使用逗号拼接
            log.info("Found {} tables: {}", tables.size(), result);
            return result;
        } catch (Exception e) {
            log.error("Error listing tables", e);
            return "Error listing tables: " + e.getMessage();
        }
    }

    public ToolCallback toolCallback() {
        String description = "列出数据库中所有可用的表。在返回给用户信息前，请先使用此工具了解有哪些表，然后根据你的分析输出合适的结果。此工具的返回结果是JSON格式的表对象列表，tableName是表编码，remarks是表说明";
        return FunctionToolCallback.builder("show all tables", this)
                                   .description(description)
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("请列出所有表")
    public static final class Request {

        @JsonProperty(value = "dummy", required = false)
        @JsonPropertyDescription("这是一个占位参数，不需要传值，请传空字符串或忽略。")
        private final String dummy;

        public Request(
                @JsonProperty(value = "dummy", required = false)
                @JsonPropertyDescription("这是一个占位参数，不需要传值，请传空字符串或忽略。") String dummy) {
            this.dummy = dummy;
        }

        @JsonProperty(value = "dummy", required = false)
        @JsonPropertyDescription("这是一个占位参数，不需要传值，请传空字符串或忽略。")
        public String dummy() {
            return dummy;
        }

        @Override
        public String toString() {
            return "Request[" +
                    "dummy=" + dummy + ']';
        }

    }

}
