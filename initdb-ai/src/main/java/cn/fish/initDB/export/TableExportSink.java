package cn.fish.initDB.export;

import java.util.Map;

/**
 * 将 JDBC 流式查询结果写入表格类导出（CSV、XLSX 等）的公共契约。
 */
public interface TableExportSink {

    void writeHeader(Iterable<String> columnNames);

    void writeDataRow(Map<String, Object> row);

    void writeNoDataRow();
}
