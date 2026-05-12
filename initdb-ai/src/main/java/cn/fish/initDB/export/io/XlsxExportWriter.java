package cn.fish.initDB.export.io;

import cn.fish.common.config.ExportConfig;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 使用 Apache POI {@link SXSSFWorkbook} 将查询结果流式写入 xlsx。
 */
public final class XlsxExportWriter implements AutoCloseable, TableExportSink {

    private final SXSSFWorkbook workbook;
    private final Sheet sheet;
    private final List<String> columns = new ArrayList<>();
    private int nextRowIndex;

    public XlsxExportWriter(ExportConfig properties) {
        this.workbook = new SXSSFWorkbook(properties.getSxssfRowAccessWindowSize());
        this.sheet = workbook.createSheet("export");
    }

    @Override
    public void writeHeader(Iterable<String> columnNames) {
        columns.clear();
        columnNames.forEach(columns::add);
        Row row = sheet.createRow(nextRowIndex++);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(columns.get(i));
        }
    }

    @Override
    public void writeDataRow(Map<String, Object> row) {
        Row excelRow = sheet.createRow(nextRowIndex++);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = excelRow.createCell(i);
            Object v = row.get(columns.get(i));
            if (v == null) {
                cell.setBlank();
            } else if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
            } else if (v instanceof Boolean b) {
                cell.setCellValue(b);
            } else {
                cell.setCellValue(String.valueOf(v));
            }
        }
    }

    @Override
    public void writeNoDataRow() {
        Row row = sheet.createRow(nextRowIndex++);
        row.createCell(0).setCellValue("（无数据行）");
    }

    /**
     * 写入输出流；不关闭 {@code os}（由调用方关闭，便于 OSS 等流式上传）。
     */
    public void writeTo(OutputStream os) throws IOException {
        workbook.write(os);
        os.flush();
    }

    public void writeTo(Path path) throws IOException {
        try (OutputStream os = Files.newOutputStream(path)) {
            writeTo(os);
        }
    }

    @Override
    public void close() throws IOException {
        workbook.dispose();
        workbook.close();
    }
}
