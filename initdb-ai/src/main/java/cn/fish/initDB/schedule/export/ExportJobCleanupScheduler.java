package cn.fish.initDB.schedule.export;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.initDB.entity.ExportJob;
import cn.fish.initDB.enums.ExportJobStatus;
import cn.fish.initDB.repository.ExportJobRepository;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 定期将过期导出任务标记为 {@link ExportJobStatus#EXPIRED} 并删除 Serva 文件。
 */
@Slf4j
@Component
public class ExportJobCleanupScheduler {

    private final ExportJobRepository exportJobRepository;
    private final ServaFile servaFile;

    public ExportJobCleanupScheduler(ExportJobRepository exportJobRepository, ServaFile servaFile) {
        this.exportJobRepository = exportJobRepository;
        this.servaFile = servaFile;
    }

    @Scheduled(fixedDelayString = "${initdb.export.cleanup-interval-ms:3600000}")
    public void cleanup() {
        try {
            cleanupExpiredExports();
        } catch (Exception e) {
            log.warn("export job cleanup failed", e);
        }
    }

    private void cleanupExpiredExports() {
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
}
