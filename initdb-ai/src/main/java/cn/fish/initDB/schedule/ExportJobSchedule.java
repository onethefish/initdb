package cn.fish.initDB.schedule;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.database.service.DataBaseService;
import cn.fish.common.config.ExportConfig;
import cn.fish.initDB.entity.ExportJob;
import cn.fish.initDB.enums.ExportFormat;
import cn.fish.initDB.enums.ExportJobStatus;
import cn.fish.initDB.export.io.CsvExportWriter;
import cn.fish.initDB.export.io.TableExportSink;
import cn.fish.initDB.export.io.XlsxExportWriter;
import cn.fish.initDB.repository.ExportJobRepository;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轮询 {@link ExportJobStatus#PENDING} 任务，执行 JDBC 流式查询并通过 {@link ServaFile#uploadDirect} 写入最终存储，
 * 不依赖本地 {@link java.nio.file.Path}，便于后续切换 OSS 等实现。
 */
@Slf4j
@Component
public class ExportJobSchedule {

    private final ExportJobRepository exportJobRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final DataBaseService dataBaseService;
    private final ServaFile servaFile;
    private final ExportConfig exportConfig;

    public ExportJobSchedule(
            ExportJobRepository exportJobRepository,
            ChatSessionRepository chatSessionRepository,
            DataBaseService dataBaseService,
            ServaFile servaFile,
            ExportConfig exportConfig) {
        this.exportJobRepository = exportJobRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.dataBaseService = dataBaseService;
        this.servaFile = servaFile;
        this.exportConfig = exportConfig;
    }

    @Scheduled(fixedDelayString = "${initdb.export.poll-interval-ms:3000}")
    public void pollPendingJobs() {
        ExportJob job = exportJobRepository.pollOnePending();
        if (job == null) {
            return;
        }
        try {
            execute(job);
        } catch (Exception e) {
            log.error("export job failed, id={}", job.getId(), e);
            markFailed(job, e);
        }
    }
    @Scheduled(fixedDelayString = "${initdb.export.cleanup-interval-ms:3600000}")
    public void cleanup() {
        LocalDateTime now = LocalDateTime.now();
        var list = exportJobRepository.listExpiredForCleanup(now);
        for (ExportJob job : list) {
            if (StrUtil.isNotBlank(job.getServaFileId())) {
                try {
                    servaFile.delete(job.getServaFileId());
                } catch (Exception e) {
                    log.warn("delete serva export file failed, jobId={}, fileId={}", job.getId(), job.getServaFileId(), e);
                }
            }
            ExportJob patch = new ExportJob();
            patch.setId(job.getId());
            patch.setStatus(ExportJobStatus.EXPIRED.name());
            patch.setFinishedAt(job.getFinishedAt() != null ? job.getFinishedAt() : now);
            exportJobRepository.updateById(patch);
        }
    }

    private void markFailed(ExportJob job, Exception e) {
        job.setStatus(ExportJobStatus.FAILED.name());
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        job.setErrorMessage(StrUtil.subPre(msg, 4000));
        job.setFinishedAt(LocalDateTime.now());
        exportJobRepository.updateById(job);
    }

    private void execute(ExportJob job) throws IOException {
        ChatSession session = chatSessionRepository.queryUnique(job.getSessionId());
        if (session == null) {
            throw new IllegalStateException("会话不存在或已失效。");
        }
        ExportFormat format = ExportFormat.valueOf(job.getFormat());
        long[] rows = new long[1];
        String fileId = servaFile.uploadDirect(out -> {
            rows[0] = switch (format) {
                case CSV -> streamToCsv(session, job.getExecutedSql(), out);
                case XLSX -> streamToXlsx(session, job.getExecutedSql(), out);
            };
        });
        job.setServaFileId(fileId);
        job.setRowCount(rows[0]);
        job.setStatus(ExportJobStatus.READY.name());
        job.setFinishedAt(LocalDateTime.now());
        exportJobRepository.updateById(job);
    }

    private long streamToCsv(ChatSession session, String sql, OutputStream out) throws IOException {
        try (CsvExportWriter writer = new CsvExportWriter(out, exportConfig)) {
            return streamQueryToSink(session, sql, writer);
        }
    }

    private long streamToXlsx(ChatSession session, String sql, OutputStream out) throws IOException {
        try (XlsxExportWriter xlsx = new XlsxExportWriter(exportConfig)) {
            long rowCount = streamQueryToSink(session, sql, xlsx);
            xlsx.writeTo(out);
            return rowCount;
        }
    }

    /**
     * 流式查询并写入 {@link TableExportSink}；返回写入的数据行数（无数据时为 0，仍会写入占位行）。
     */
    private long streamQueryToSink(ChatSession session, String sql, TableExportSink sink) {
        AtomicLong rows = new AtomicLong();
        dataBaseService.queryTableDataStreaming(session, sql, row -> {
            if (rows.get() == 0) {
                sink.writeHeader(new ArrayList<>(row.keySet()));
            }
            sink.writeDataRow(row);
            rows.incrementAndGet();
        });
        if (rows.get() == 0) {
            sink.writeNoDataRow();
        }
        return rows.get();
    }
}
