package cn.fish.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 异步导出相关配置，前缀 {@code initdb.export}。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "initdb.export")
public class ExportConfig {

    /**
     * 单次导出 SQL 行数硬顶（服务端钳制用户请求）
     */
    private int maxRows = 1_000_000;

    /**
     * 任务与文件记录过期时间（小时）
     */
    private int jobTtlHours = 24;

    /**
     * 处理 PENDING 任务的轮询间隔（毫秒）
     */
    private long pollIntervalMs = 3000L;

    /**
     * 过期清理调度间隔（毫秒）
     */
    private long cleanupIntervalMs = 3_600_000L;

    /**
     * SXSSFWorkbook 内存中保留的行数窗口
     */
    private int sxssfRowAccessWindowSize = 500;

    /**
     * CSV 是否写 UTF-8 BOM（便于 Excel 打开中文）
     */
    private boolean csvUtf8Bom = true;

    /**
     * Excel 导出单列显示宽度下限（按字符数近似，POI 列宽单位为 1/256 字符宽）
     */
    private int xlsxMinColumnWidthChars = 16;

    /**
     * Excel 导出单列显示宽度上限（字符数近似）。更长内容不再加宽列、不换行，全文仍保存在单元格内。
     */
    private int xlsxMaxColumnWidthChars = 80;
}
