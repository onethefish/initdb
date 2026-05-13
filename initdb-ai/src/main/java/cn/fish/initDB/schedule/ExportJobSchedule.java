package cn.fish.initDB.schedule;

import cn.fish.initDB.service.ExportJobProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 导出任务的后台触发：定时兜底轮询与过期清理，具体处理见 {@link ExportJobProcessor}；
 * 提交后的及时唤醒见 {@link cn.fish.initDB.event.listen.ExportJobPendingListener}。
 */
@Component
public class ExportJobSchedule {

    private final ExportJobProcessor exportJobProcessor;

    public ExportJobSchedule(ExportJobProcessor exportJobProcessor) {
        this.exportJobProcessor = exportJobProcessor;
    }

    @Scheduled(fixedDelayString = "${initdb.export.poll-interval-ms:60000}")
    public void pollPendingJobs() {
        exportJobProcessor.drainPendingJobs();
    }

    @Scheduled(fixedDelayString = "${initdb.export.cleanup-interval-ms:3600000}")
    public void cleanup() {
        exportJobProcessor.cleanupExpiredJobs();
    }
}
