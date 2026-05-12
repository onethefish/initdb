package cn.fish.initDB.enums;

/**
 * 与表 {@code export_job.status} 取值一致。
 */
public enum ExportJobStatus {
    PENDING,
    RUNNING,
    READY,
    FAILED,
    EXPIRED
}
