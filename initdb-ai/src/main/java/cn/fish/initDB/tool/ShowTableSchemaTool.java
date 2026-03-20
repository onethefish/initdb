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
import cn.fish.initDB.entity.TableColumn;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * 查询表结构的工具
 */
@Slf4j
@Component
public class ShowTableSchemaTool implements BiFunction<ShowTableSchemaTool.Request, ToolContext, String> {


    private final DataSource dataSource;

    public ShowTableSchemaTool(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("ShowTableSchemaTool::apply");
        log.info("request={}", request);
        log.info("toolContext={}", toolContext);
        List<String> tableNames = Arrays.stream(request.tables().split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .toList();
        if (tableNames.isEmpty()) {
            return "No table names provided. Please specify table names separated by commas.";
        }
        List<Table> tables = new ArrayList<>();
        for (String tableName : tableNames) {
            Table table = getTableSchema(tableName);
            if (table != null) {
                tables.add(table);
            }
        }
        String result = JSON.toJSONString(tables, JSONWriter.Feature.IgnoreEmpty);
        log.info("Found {} tables: {}", tables.size(), result);
        return result;

    }

    private Table getTableSchema(String input) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData databaseMetaData = conn.getMetaData();
            String catalog = conn.getCatalog();
            //            ArrayList<Table> resultList = new ArrayList();
            Table table = new Table();
            table.setTableName(sanitizeTableName(input));
            String tableName = table.getTableName();
            // 获取主键
            ResultSet metaDataPrimaryKeys = databaseMetaData.getPrimaryKeys(catalog, conn.getMetaData().getUserName(), tableName);
            while (metaDataPrimaryKeys.next()) {
                String column_name = metaDataPrimaryKeys.getString("COLUMN_NAME");
                Integer key_seq = metaDataPrimaryKeys.getInt("KEY_SEQ");
                table.addPrimaryKeysMap(key_seq, column_name);
            }
            // 获取索引
            ResultSet indexInfos = databaseMetaData.getIndexInfo(catalog, conn.getMetaData().getUserName(), tableName, false, true);
            int key_seq = 1;
            while (indexInfos.next()) {
                String index_name = indexInfos.getString("INDEX_NAME");
                String column_name = indexInfos.getString("COLUMN_NAME");
                //                        Integer key_seq = indexInfos.getInt("SEQ_IN_INDEX");
                key_seq++;
                if (!"PRIMARY".equalsIgnoreCase(index_name)) {
                    table.setIndexMapMap(index_name, key_seq, column_name);
                }
            }
            table.indexMapSort();
            // 获取表字段
            ResultSet columns = databaseMetaData.getColumns(catalog, conn.getMetaData().getUserName(), tableName, "%");
            while (columns.next()) {
                TableColumn tableColumn = new TableColumn();
                String column_name = columns.getString("COLUMN_NAME");
                String type_name = columns.getString("TYPE_NAME");// 数据类型
                String column_size = columns.getString("COLUMN_SIZE");// 长度
                String decimal_digits = columns.getString("DECIMAL_DIGITS");// 精度
                String column_def = columns.getString("COLUMN_DEF");// 默认值
                boolean is_nullable = columns.getBoolean("IS_NULLABLE");
                String remarks = columns.getString("REMARKS");// 注释
                tableColumn.setColumnInfo(column_name, type_name, column_size, decimal_digits, column_def, is_nullable);
                tableColumn.setRemarks(remarks);
                table.addTableColumnMap(column_name, tableColumn);
            }
            table.tableColumnSort();
            table.dealColumn();
            // Get CREATE TABLE statement
            //            String result = table.toString();
            //            log.info(result);
            return table;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
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
