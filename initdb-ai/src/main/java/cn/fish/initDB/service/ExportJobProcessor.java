package cn.fish.initDB.service;

/**
 * 异步导出队列处理：抢占 {@link cn.fish.initDB.enums.ExportJobStatus#PENDING} 任务并执行、以及过期清理。
 */
public interface ExportJobProcessor {

    void drainPendingJobs();

    void cleanupExpiredJobs();
}
