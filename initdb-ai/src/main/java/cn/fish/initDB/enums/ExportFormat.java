package cn.fish.initDB.enums;

import cn.hutool.core.util.StrUtil;
import org.springframework.http.MediaType;

import java.util.Locale;

/**
 * 导出文件格式（与 {@code export_job.format} 列一致）。
 */
public enum ExportFormat {
    CSV,
    XLSX;

    public static ExportFormat fromClient(String raw) {
        if (StrUtil.isBlank(raw)) {
            return CSV;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if ("CSV".equals(u)) {
            return CSV;
        }
        if ("XLSX".equals(u) || "EXCEL".equals(u)) {
            return XLSX;
        }
        throw new IllegalArgumentException("不支持的导出格式：" + raw + "，请使用 CSV 或 XLSX。");
    }

    public String fileSuffix() {
        return switch (this) {
            case CSV -> ".csv";
            case XLSX -> ".xlsx";
        };
    }

    public MediaType mediaType() {
        return switch (this) {
            case CSV -> new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);
            case XLSX -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        };
    }
}
