package cn.fish.initDB.service.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.common.config.ExportConfig;
import cn.fish.database.sql.SelectSqlRowLimiter;
import cn.fish.database.sql.SqlDialect;
import cn.fish.database.sql.SqlDialectResolver;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.fish.initDB.entity.ExportJob;
import cn.fish.initDB.entity.ExportJobCreateRequest;
import cn.fish.initDB.entity.ExportJobDownloadOpen;
import cn.fish.initDB.entity.ExportJobView;
import cn.fish.initDB.enums.ExportFormat;
import cn.fish.initDB.enums.ExportJobStatus;
import cn.fish.initDB.repository.ExportJobRepository;
import cn.fish.initDB.service.ExportJobService;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class ExportJobServiceImpl implements ExportJobService {

    private final ExportJobRepository exportJobRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final AgentDatasourceRepository agentDatasourceRepository;
    private final ExportSqlGuardService exportSqlGuardService;
    private final ExportConfig exportConfig;
    private final ServaFile servaFile;

    public ExportJobServiceImpl(
            ExportJobRepository exportJobRepository,
            ChatSessionRepository chatSessionRepository,
            AgentDatasourceRepository agentDatasourceRepository,
            ExportSqlGuardService exportSqlGuardService,
            ExportConfig exportConfig,
            ServaFile servaFile) {
        this.exportJobRepository = exportJobRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.agentDatasourceRepository = agentDatasourceRepository;
        this.exportSqlGuardService = exportSqlGuardService;
        this.exportConfig = exportConfig;
        this.servaFile = servaFile;
    }

    @Override
    public ExportJobView add(ExportJobCreateRequest request) {
        ExportFormat format = ExportFormat.fromClient(request.getFormat());
        int cap = exportConfig.getMaxRows();
        if (request.getMaxRows() != null) {
            cap = Math.min(cap, request.getMaxRows());
        }
        String rawSql = request.getSql().trim();
        if (!exportSqlGuardService.isAllowed(rawSql)) {
            throw new CommonException(exportSqlGuardService.checkVerdict(rawSql));
        }
        ChatSession session = chatSessionRepository.queryUnique(request.getSessionId());
        if (session == null) {
            throw new CommonException("未找到会话，请先连接数据库。");
        }
        SqlDialect dialect = SqlDialectResolver.fromChatSession(session, agentDatasourceRepository);
        String executedSql = SelectSqlRowLimiter.ensureSelectRowLimit(rawSql, cap, dialect);

        LocalDateTime now = LocalDateTime.now();
        ExportJob job = new ExportJob();
        job.setSessionId(request.getSessionId());
        job.setFormat(format.name());
        job.setMaxRows(cap);
        job.setSubmittedSql(rawSql);
        job.setExecutedSql(executedSql);
        job.setStatus(ExportJobStatus.PENDING.name());
        job.setCreatedTime(now);
        job.setExpiresAt(now.plusHours(exportConfig.getJobTtlHours()));
        exportJobRepository.save(job);
        return toView(job);
    }

    @Override
    public ExportJobView queryUnique(String jobId, String sessionId) {
        ExportJob job = requireOwnedJob(jobId, sessionId);
        return toView(job);
    }

    private ExportJob requireDownloadable(String jobId, String sessionId) {
        ExportJob job = requireOwnedJob(jobId, sessionId);
        if (!ExportJobStatus.READY.name().equals(job.getStatus())) {
            throw new CommonException("任务未完成或已失效，当前状态：" + job.getStatus());
        }
        if (StrUtil.isBlank(job.getServaFileId())) {
            throw new CommonException("导出文件不存在。");
        }
        if (job.getExpiresAt() != null && job.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CommonException("导出已过期。");
        }
        return job;
    }

    @Override
    public ExportJobDownloadOpen download(String jobId, String sessionId) throws IOException {
        ExportJob job = requireDownloadable(jobId, sessionId);
        ExportFormat format = ExportFormat.valueOf(job.getFormat());
        return new ExportJobDownloadOpen(servaFile.getInputStream(job.getServaFileId()), format);
    }

    private ExportJob requireOwnedJob(String jobId, String sessionId) {
        if (StrUtil.isBlank(jobId) || StrUtil.isBlank(sessionId)) {
            throw new CommonException("jobId 与 sessionId 不能为空。");
        }
        ExportJob job = exportJobRepository.getById(jobId);
        if (job == null) {
            throw new CommonException("任务不存在。");
        }
        if (!sessionId.equals(job.getSessionId())) {
            throw new CommonException("无权访问该导出任务。");
        }
        return job;
    }

    private static ExportJobView toView(ExportJob job) {
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
}
