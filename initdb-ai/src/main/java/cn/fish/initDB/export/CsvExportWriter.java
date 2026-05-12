package cn.fish.initDB.export;

import cn.hutool.core.text.csv.CsvUtil;
import cn.hutool.core.text.csv.CsvWriter;
import cn.fish.common.config.ExportConfig;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 使用 Hutool CSV 将查询结果流式写入文件或输出流。
 */
public final class CsvExportWriter implements AutoCloseable, TableExportSink {

    private final CsvWriter csvWriter;
    private final List<String> columns = new ArrayList<>();

    /**
     * 写入本地文件；关闭 writer 时会关闭文件流。
     */
    public CsvExportWriter(Path path, ExportConfig properties) throws IOException {
        this(Files.newOutputStream(path), properties, true);
    }

    /**
     * 写入已有输出流（例如 {@link cn.fish.cloud.serva.file.operate.ServaFile#uploadDirect} 提供的流）。
     * 关闭 writer 时<strong>不会</strong>关闭 {@code out}，由调用方关闭。
     */
    public CsvExportWriter(OutputStream out, ExportConfig properties) throws IOException {
        this(out, properties, false);
    }

    private CsvExportWriter(OutputStream out, ExportConfig properties, boolean closeUnderlying) throws IOException {
        OutputStream sink = closeUnderlying ? out : nonClosing(out);
        if (properties.isCsvUtf8Bom()) {
            sink.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        }
        this.csvWriter = CsvUtil.getWriter(new OutputStreamWriter(sink, StandardCharsets.UTF_8));
    }

    private static OutputStream nonClosing(OutputStream out) {
        return new FilterOutputStream(out) {
            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }

    @Override
    public void writeHeader(Iterable<String> columnNames) {
        columns.clear();
        columnNames.forEach(columns::add);
        csvWriter.write(columns.toArray(new String[0]));
    }

    @Override
    public void writeDataRow(Map<String, Object> row) {
        String[] cells = new String[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            Object v = row.get(columns.get(i));
            cells[i] = v == null ? "" : String.valueOf(v);
        }
        csvWriter.write(cells);
    }

    @Override
    public void writeNoDataRow() {
        csvWriter.write(new String[]{"（无数据行）"});
    }

    @Override
    public void close() {
        csvWriter.close();
    }
}
