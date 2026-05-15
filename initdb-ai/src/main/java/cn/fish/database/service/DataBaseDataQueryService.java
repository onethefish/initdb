package cn.fish.database.service;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.common.config.QuerySqlCheckConfig;
import cn.fish.database.dto.DataSqlQueryRequest;
import cn.fish.database.dto.DataSqlValidateRequest;
import cn.fish.database.dto.DataSqlValidateResponse;
import cn.fish.database.sql.SelectSqlPaginationWrapper;
import cn.fish.database.sql.SqlDialect;
import cn.fish.database.sql.SqlDialectResolver;
import cn.fish.database.sql.SqlSelectSyntaxPreCheck;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.fish.initDB.service.impl.ExportSqlGuardService;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 会话内只读 SQL 分页与校验（与导出使用同一套 {@link ExportSqlGuardService}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataBaseDataQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 500;
    private static final int MAX_PAGE_NUM = 100_000;

    private final DataBaseService dataBaseService;
    private final ChatSessionRepository chatSessionRepository;
    private final ExportSqlGuardService exportSqlGuardService;
    private final AgentDatasourceRepository agentDatasourceRepository;
    private final QuerySqlCheckConfig querySqlCheckConfig;

    public Page<Map<String, Object>> queryPage(DataSqlQueryRequest request) {
        ChatSession session = requireSession(request.getSessionId());
        String rawSql = StrUtil.trim(request.getSql());
        assertSqlAllowed(rawSql);

        int pageNum = clampPageNum(request.getPageNum());
        int pageSize = clampPageSize(request.getPageSize());
        String inner = SelectSqlPaginationWrapper.stripTrailingSemicolon(rawSql);
        if (StrUtil.isBlank(inner)) {
            throw new CommonException("SQL 为空。");
        }

        SqlDialect dialect = SqlDialectResolver.fromChatSession(session, agentDatasourceRepository);
        String countSql = SelectSqlPaginationWrapper.wrapAsCountSubquery(inner);
        long total = executeCount(session, countSql);

        long offset = (long) (pageNum - 1) * pageSize;
        if (offset >= total) {
            Page<Map<String, Object>> empty = new Page<>(pageNum, pageSize, total);
            empty.setRecords(List.of());
            return empty;
        }

        String pageSql = SelectSqlPaginationWrapper.wrapAsPagedSubquery(inner, dialect, offset, pageSize);
        List<Map<String, Object>> records = dataBaseService.queryTableData(session, pageSql);

        Page<Map<String, Object>> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(records);
        return page;
    }

    public DataSqlValidateResponse validate(DataSqlValidateRequest request) {
        requireSession(request.getSessionId());
        String rawSql = StrUtil.trim(request.getSql());
        if (StrUtil.isBlank(rawSql)) {
            return new DataSqlValidateResponse(false, "SQL 为空。");
        }
        String verdict = exportSqlGuardService.checkVerdict(rawSql);
        boolean ok = StrUtil.isNotBlank(verdict) && verdict.contains("校验成功");
        return new DataSqlValidateResponse(ok, verdict);
    }

    private ChatSession requireSession(String sessionId) {
        ChatSession session = chatSessionRepository.queryUnique(sessionId);
        if (ObjectUtil.isNull(session)) {
            throw new CommonException("未找到会话，请先创建对话。");
        }
        return session;
    }

    private void assertSqlAllowed(String rawSql) {
        var syntaxFail = SqlSelectSyntaxPreCheck.tryParseFailureVerdict(rawSql, querySqlCheckConfig.getMaxSqlChars());
        if (syntaxFail.isPresent()) {
            throw new CommonException(syntaxFail.get());
        }
        if (!exportSqlGuardService.isAllowed(rawSql)) {
            throw new CommonException(exportSqlGuardService.checkVerdict(rawSql));
        }
    }

    private long executeCount(ChatSession session, String countSql) {
        List<Map<String, Object>> rows = dataBaseService.queryTableData(session, countSql);
        if (rows == null || rows.isEmpty()) {
            return 0L;
        }
        Map<String, Object> row = rows.get(0);
        if (row == null || row.isEmpty()) {
            return 0L;
        }
        Object v = row.values().iterator().next();
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }

    private static int clampPageNum(Integer pageNum) {
        int n = pageNum == null ? 1 : pageNum;
        if (n < 1) {
            return 1;
        }
        return Math.min(n, MAX_PAGE_NUM);
    }

    private static int clampPageSize(Integer pageSize) {
        int s = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (s < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(s, MAX_PAGE_SIZE);
    }
}
