package cn.fish.initDB.service.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.common.config.ExportConfig;
import cn.fish.database.service.DataBaseService;
import cn.fish.initDB.entity.ExportJob;
import cn.fish.initDB.enums.ExportFormat;
import cn.fish.initDB.enums.ExportJobStatus;
import cn.fish.initDB.export.CsvExportWriter;
import cn.fish.initDB.export.TableExportSink;
import cn.fish.initDB.export.XlsxExportWriter;
import cn.fish.initDB.repository.ExportJobRepository;
import cn.fish.initDB.service.ExportJobProcessor;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 导出队列的实际处理：从 {@link ExportJobStatus#PENDING} 抢占并执行流式导出，
 * 以及过期任务清理。由 {@link cn.fish.initDB.schedule.ExportJobSchedule} 与
 * {@link cn.fish.initDB.event.listen.ExportJobPendingListener} 触发。
 */
@Slf4j
@Service
public class ExportJobProcessorImpl implements ExportJobProcessor {

    private final Object drainLock = new Object();

    private final ExportJobRepository exportJobRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final DataBaseService dataBaseService;
    private final ServaFile servaFile;
    private final ExportConfig exportConfig;

    public ExportJobProcessorImpl(
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

    @Override
    public void drainPendingJobs() {
        synchronized (drainLock) {
            for (;;) {
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
        }
    }

    @Override
    public void cleanupExpiredJobs() {
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
