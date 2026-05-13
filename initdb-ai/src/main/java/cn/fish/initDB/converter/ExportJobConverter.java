package cn.fish.initDB.converter;

import cn.fish.initDB.entity.ExportJob;
import cn.fish.initDB.entity.ExportJobCreateRequest;
import cn.fish.initDB.entity.ExportJobView;
import cn.fish.initDB.enums.ExportFormat;
import cn.fish.initDB.enums.ExportJobStatus;
import cn.hutool.core.util.StrUtil;

import java.time.LocalDateTime;

public class ExportJobConverter {

    private ExportJobConverter() {
    }

    public static ExportJobView toView(ExportJob job) {
        return ExportJobView.builder()
                            .id(job.getId())
                            .sessionId(job.getSessionId())
                            .format(job.getFormat())
                            .maxRows(job.getMaxRows())
                            .status(job.getStatus())
                            .rowCount(job.getRowCount())
                            .errorMessage(job.getErrorMessage())
                            .createdTime(job.getCreatedTime())
                            .expiresAt(job.getExpiresAt())
                            .finishedAt(job.getFinishedAt())
                            .downloadReady(ExportJobStatus.READY.name().equals(job.getStatus()) && StrUtil.isNotBlank(job.getServaFileId()))
                            .build();
    }

    public static ExportJob toPendingEntity(
            ExportJobCreateRequest request,
            ExportFormat format,
            int maxRows,
            String submittedSql,
            String executedSql,
            LocalDateTime createdTime,
            int jobTtlHours) {
        ExportJob job = new ExportJob();
        job.setSessionId(request.getSessionId());
        job.setFormat(format.name());
        job.setMaxRows(maxRows);
        job.setSubmittedSql(submittedSql);
        job.setExecutedSql(executedSql);
        job.setStatus(ExportJobStatus.PENDING.name());
        job.setCreatedTime(createdTime);
        job.setExpiresAt(createdTime.plusHours(jobTtlHours));
        return job;
    }
}
