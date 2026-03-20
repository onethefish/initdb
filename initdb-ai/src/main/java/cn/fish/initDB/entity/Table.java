package cn.fish.initDB.entity;

import com.alibaba.fastjson2.JSONArray;
import lombok.Data;

import java.util.*;

@Data
public class Table {

    private String tableName;
    private String remarks;
    private Map<Integer, String> primaryKeysMap = new TreeMap<>(Integer::compareTo);
    private List<String> primaryKeys = new ArrayList<>();
    private Map<String, Map<Integer, String>> indexMapMap = new HashMap<>();
    private Map<String, List<String>> indexMap = new HashMap<>();
    private Map<String, TableColumn> tableColumnMap = new HashMap<>();
    private List<TableColumn> tableColumnList = new ArrayList<>();
    private List<List<Object>> tableDataList = new ArrayList<>();

    @Override
    public String toString() {
        StringJoiner columnList = getTableColumnStr();
        return "Table{" +
                "tableName='" + tableName + '\'' +
                ", remarks='" + remarks + '\'' +
                ", primaryKeys=" + primaryKeys +
                ", indexMap=" + indexMap +
                ", tableColumnList=" + columnList +
                '}';
    }

    private StringJoiner getTableColumnStr() {
        StringJoiner joiner = new StringJoiner(",");
        for (TableColumn tableColumn : tableColumnList) {
            //            String columnName = tableColumn.getColumnName();
            String columnCode = tableColumn.getColumnName();
            String remarks = tableColumn.getRemarks();
            joiner.add(columnCode + ":" + remarks);
        }
        return joiner;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName.toUpperCase();
    }

    public void addTableColumnMap(String columnName, TableColumn tableColumn) {
        tableColumnMap.put(columnName, tableColumn);
    }

    public void addPrimaryKeysMap(Integer seq_in_index, String columnName) {
        primaryKeysMap.put(seq_in_index, columnName);
    }

    public void setIndexMapMap(String indexName, Integer seq_in_index, String columnName) {
        Map<Integer, String> integerStringMap = indexMapMap.computeIfAbsent(indexName, k -> new TreeMap<>(Integer::compareTo));
        integerStringMap.put(seq_in_index, columnName);
    }

    public void indexMapSort() {
        for (Map.Entry<String, Map<Integer, String>> entry : indexMapMap.entrySet()) {
            String k = entry.getKey();
            Map<Integer, String> v = entry.getValue();
            List<String> indexList = new ArrayList<>();
            v.forEach((k1, k2) -> indexList.add(k2));
            indexMap.put(k, indexList);
        }
    }

    public void primaryKeysSort() {
        primaryKeysMap.forEach((k, v) -> primaryKeys.add(v));
    }

    public void tableColumnSort() {
        for (String columnCode : primaryKeys) {
            TableColumn remove = tableColumnMap.remove(columnCode);
            tableColumnList.add(remove);
        }
        for (Map.Entry<String, List<String>> entry : indexMap.entrySet()) {
            String k = entry.getKey();
            List<String> indexList = entry.getValue();
            for (String tableColumn : indexList) {
                if (tableColumnMap.containsKey(tableColumn)) {
                    TableColumn remove = tableColumnMap.remove(tableColumn);
                    tableColumnList.add(remove);
                }
            }
        }
        // 剩下的
        for (Map.Entry<String, TableColumn> entry : tableColumnMap.entrySet()) {
            TableColumn v = entry.getValue();
            tableColumnList.add(v);
        }
    }

    public void dealColumn() {
        for (TableColumn tableColumn : tableColumnList) {
            String columnCode = tableColumn.getColumnName();
            List<String> indexNames = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : indexMap.entrySet()) {
                String indexName = entry.getKey();
                List<String> indexColumn = entry.getValue();
                if (indexColumn.contains(columnCode)) {
                    indexNames.add(indexName);
                }
            }
            if (primaryKeys.contains(columnCode)) {
                tableColumn.setPk(true);
            }
            tableColumn.setIndexNames(indexNames);
        }
    }

    public void addRowTableDataList(List<Object> rowList) {
        tableDataList.add(rowList);
    }

    public String toStringTableData() {
        StringJoiner columnList = getTableColumnStr();
        JSONArray jsonArray = new JSONArray(tableDataList);
        return '\n' + "Table{" +
                "tableCode='" + tableName + '\'' +
                ", tableName='" + remarks + '\'' +
                ", tableColumnList=" + columnList +
                '}' + '\n' + jsonArray.toJSONString();
    }
}
