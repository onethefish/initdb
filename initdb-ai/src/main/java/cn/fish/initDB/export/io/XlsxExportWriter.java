package cn.fish.initDB.export.io;

import cn.fish.common.config.ExportConfig;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
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
 * <p>
 * 表头：黑体加粗、水平垂直居中、粗边框；数据区：黑字、粗边框；不换行。
 * 列宽按内容逐步放宽，不低于 {@link ExportConfig#getXlsxMinColumnWidthChars()}，
 * 不超过 {@link ExportConfig#getXlsxMaxColumnWidthChars()}（超出不再加宽，单元格内仍保留全文）。
 */
public final class XlsxExportWriter implements AutoCloseable, TableExportSink {

    private static final int EXCEL_MAX_COLUMN_WIDTH_UNITS = 255 * 256;

    private final SXSSFWorkbook workbook;
    private final Sheet sheet;
    private final ExportConfig exportConfig;
    private final List<String> columns = new ArrayList<>();
    private int nextRowIndex;

    private CellStyle headerStyle;
    private CellStyle dataStyle;
    /** 各列用于估算列宽的字符数（近似） */
    private int[] colCharWidths;

    public XlsxExportWriter(ExportConfig properties) {
        this.exportConfig = properties;
        this.workbook = new SXSSFWorkbook(properties.getSxssfRowAccessWindowSize());
        this.sheet = workbook.createSheet("export");
    }

    private void ensureStyles() {
        if (headerStyle != null) {
            return;
        }
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.BLACK.getIndex());
        headerFont.setFontHeightInPoints((short) 11);

        headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setWrapText(false);
        applyThickBorders(headerStyle);

        Font dataFont = workbook.createFont();
        dataFont.setBold(false);
        dataFont.setColor(IndexedColors.BLACK.getIndex());
        dataFont.setFontHeightInPoints((short) 11);

        dataStyle = workbook.createCellStyle();
        dataStyle.setFont(dataFont);
        dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        dataStyle.setWrapText(false);
        applyThickBorders(dataStyle);
    }

    /** 表头与数据单元格统一使用粗线边框 */
    private static void applyThickBorders(CellStyle style) {
        BorderStyle b = BorderStyle.THICK;
        style.setBorderTop(b);
        style.setBorderBottom(b);
        style.setBorderLeft(b);
        style.setBorderRight(b);
    }

    private int minChars() {
        return Math.max(4, exportConfig.getXlsxMinColumnWidthChars());
    }

    private int maxChars() {
        return Math.max(minChars(), exportConfig.getXlsxMaxColumnWidthChars());
    }

    private void bumpColWidth(int colIndex, int displayChars) {
        if (colCharWidths == null || colIndex < 0 || colIndex >= colCharWidths.length) {
            return;
        }
        int capped = Math.min(Math.max(displayChars, minChars()), maxChars());
        if (capped > colCharWidths[colIndex]) {
            colCharWidths[colIndex] = capped;
        }
    }

    private void applyFinalColumnWidths() {
        if (colCharWidths == null) {
            return;
        }
        for (int i = 0; i < colCharWidths.length; i++) {
            int chars = Math.min(Math.max(colCharWidths[i], minChars()), maxChars());
            int widthUnits = chars * 256;
            sheet.setColumnWidth(i, Math.min(widthUnits, EXCEL_MAX_COLUMN_WIDTH_UNITS));
        }
    }

    @Override
    public void writeHeader(Iterable<String> columnNames) {
        ensureStyles();
        columns.clear();
        columnNames.forEach(columns::add);
        colCharWidths = new int[columns.size()];
        int min = minChars();
        int max = maxChars();
        for (int i = 0; i < columns.size(); i++) {
            String name = columns.get(i);
            int len = name != null ? name.length() : 0;
            colCharWidths[i] = Math.min(Math.max(len + 2, min), max);
        }
        Row row = sheet.createRow(nextRowIndex++);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(columns.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    @Override
    public void writeDataRow(Map<String, Object> row) {
        ensureStyles();
        Row excelRow = sheet.createRow(nextRowIndex++);
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = excelRow.createCell(i);
            Object v = row.get(columns.get(i));
            if (v == null) {
                cell.setBlank();
            } else if (v instanceof Number n) {
                cell.setCellValue(n.doubleValue());
                bumpColWidth(i, 14);
            } else if (v instanceof Boolean b) {
                cell.setCellValue(b);
                bumpColWidth(i, 6);
            } else {
                String s = String.valueOf(v);
                cell.setCellValue(s);
                bumpColWidth(i, s.length() + 2);
            }
            cell.setCellStyle(dataStyle);
        }
    }

    @Override
    public void writeNoDataRow() {
        ensureStyles();
        Row row = sheet.createRow(nextRowIndex++);
        Cell cell = row.createCell(0);
        cell.setCellValue("（无数据行）");
        cell.setCellStyle(dataStyle);
        int w = Math.max(minChars(), 14) * 256;
        sheet.setColumnWidth(0, Math.min(w, EXCEL_MAX_COLUMN_WIDTH_UNITS));
    }

    /**
     * 写入输出流；不关闭 {@code os}（由调用方关闭，便于 OSS 等流式上传）。
     */
    public void writeTo(OutputStream os) throws IOException {
        applyFinalColumnWidths();
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
