package cn.fish.initDB.event.listen;

import cn.fish.initDB.event.ExportJobPendingEvent;
import cn.fish.initDB.service.ExportJobProcessor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 新导出任务提交并事务提交后唤醒队列处理，与 {@link cn.fish.initDB.schedule.ExportJobSchedule} 的定时兜底配合。
 */
@Component
public class ExportJobPendingListener {

    private final ExportJobProcessor exportJobProcessor;

    public ExportJobPendingListener(ExportJobProcessor exportJobProcessor) {
        this.exportJobProcessor = exportJobProcessor;
    }

    @Async("initDbExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExportJobPending(ExportJobPendingEvent event) {
        exportJobProcessor.drainPendingJobs();
    }
}
