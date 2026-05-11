package cn.fish.initDB.workflow.node;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.database.service.DataBaseService;
import cn.fish.database.sql.SelectSqlRowLimiter;
import cn.fish.database.sql.SqlDialectResolver;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.fish.initDB.constants.WorkflowConstants;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.*;

/**
 * 直连链路：执行 {@link WorkflowConstants#DB_BUNDLE_KEY_GENERATED_SQL}（位于 {@link WorkflowConstants#STATE_KEY_DB_BUNDLE} 内），
 * 通过顶层 {@link WorkflowConstants#STATE_KEY_DIRECT_EXECUTE_STREAM} 嵌入流式输出，
 * 并写入 bundle 内 {@link WorkflowConstants#DB_BUNDLE_KEY_DIRECT_ANSWER} 供落库与兜底。
 */
@Slf4j
@Component
public class DbDirectExecuteQueryNode implements NodeAction {

    // StateGraph 节点 id，勿改字符串以免破坏 checkpoint / 流式帧匹配
    public static final String GRAPH_NODE_ID = "db_direct_execute";

    private static final int MARKDOWN_CHUNK_CHARS = 900;
    private static final int maxResults = 500;

    private final DataBaseService dataBaseService;
    private final ChatSessionRepository chatSessionRepository;
    private final AgentDatasourceRepository agentDatasourceRepository;

    public DbDirectExecuteQueryNode(
            DataBaseService dataBaseService,
            ChatSessionRepository chatSessionRepository,
            AgentDatasourceRepository agentDatasourceRepository) {
        this.dataBaseService = dataBaseService;
        this.chatSessionRepository = chatSessionRepository;
        this.agentDatasourceRepository = agentDatasourceRepository;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> bundle = DbWorkflowBundle.readCopy(state);
        String sessionId = DbWorkflowBundle.bundleString(bundle, WorkflowConstants.DB_BUNDLE_KEY_SESSION_ID, "");
        String sql = DbWorkflowBundle.bundleString(bundle, WorkflowConstants.DB_BUNDLE_KEY_GENERATED_SQL, "");
        if (StrUtil.isBlank(sessionId) || StrUtil.isBlank(sql)) {
            String msg = "缺少会话或 SQL，无法执行查询。";
            return directExecuteResult(state, msg, "## 提示\n\n" + msg);
        }
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        if (ObjectUtil.isNull(chatSession)) {
            String msg = "未找到会话，请先连接数据库。";
            return directExecuteResult(state, msg, "## 提示\n\n" + msg);
        }
        String toRun = SelectSqlRowLimiter.ensureSelectRowLimit(
                sql, maxResults, SqlDialectResolver.fromChatSession(chatSession, agentDatasourceRepository));
        try {
            String header = "## 执行的 SQL\n\n```sql\n" + toRun + "\n```\n\n## 查询结果\n\n";
            String tableMd = buildMarkdownTableStreaming(chatSession, toRun);
            String full = header + tableMd;
            return directExecuteResult(state, full, fluxFromMarkdown(state, full));
        } catch (Exception e) {
            log.error("db direct execute failed", e);
            String plain = "执行查询失败：" + e.getMessage();
            String md = "## 执行的 SQL\n\n```sql\n" + toRun + "\n```\n\n## 错误\n\n" + e.getMessage();
            return directExecuteResult(state, plain, md);
        }
    }

    /**
     * 成功或失败均写入 {@link WorkflowConstants#DB_BUNDLE_KEY_DIRECT_ANSWER} 与嵌入 Flux，避免仅 state 无流式帧时前端无展示。
     */
    private static Map<String, Object> directExecuteResult(OverAllState state, String directAnswer, String streamMarkdown) {
        Flux<GraphResponse<NodeOutput>> flux = fluxFromMarkdown(state, streamMarkdown);
        return directExecuteResult(state, directAnswer, flux);
    }

    private static Map<String, Object> directExecuteResult(
            OverAllState state, String directAnswer, Flux<GraphResponse<NodeOutput>> streamFlux) {
        Map<String, Object> b = DbWorkflowBundle.readCopy(state);
        b.put(WorkflowConstants.DB_BUNDLE_KEY_DIRECT_ANSWER, directAnswer);
        DbWorkflowBundle.stripLegacyBundleKeys(b);
        Map<String, Object> out = new LinkedHashMap<>(4);
        out.put(WorkflowConstants.STATE_KEY_DIRECT_EXECUTE_STREAM, streamFlux);
        out.put(WorkflowConstants.STATE_KEY_DB_BUNDLE, b);
        return out;
    }

    private static Flux<GraphResponse<NodeOutput>> fluxFromMarkdown(OverAllState state, String markdown) {
        List<GraphResponse<NodeOutput>> parts = new ArrayList<>();
        for (String piece : chunkMarkdown(markdown, MARKDOWN_CHUNK_CHARS)) {
            parts.add(wrapChunk(state, piece));
        }
        if (parts.isEmpty()) {
            parts.add(wrapChunk(state, StrUtil.isNotBlank(markdown) ? markdown : "（无内容）"));
        }
        return Flux.fromIterable(parts);
    }

    private static GraphResponse<NodeOutput> wrapChunk(OverAllState state, String text) {
        StreamingOutput<String> out = new StreamingOutput<>(
                text,
                GRAPH_NODE_ID,
                "",
                state,
                OutputType.GRAPH_NODE_STREAMING);
        return GraphResponse.of((NodeOutput) out);
    }

    private static List<String> chunkMarkdown(String md, int maxChars) {
        List<String> parts = new ArrayList<>();
        if (StrUtil.isEmpty(md)) {
            return parts;
        }
        int i = 0;
        while (i < md.length()) {
            int end = Math.min(i + maxChars, md.length());
            if (end < md.length()) {
                int nl = md.lastIndexOf('\n', end);
                if (nl > i + maxChars / 2) {
                    end = nl + 1;
                }
            }
            parts.add(md.substring(i, end));
            i = end;
        }
        return parts;
    }

    /** 按行 JDBC 流式读取并拼 Markdown，避免整表 {@link List} 驻留内存。 */
    private String buildMarkdownTableStreaming(ChatSession chatSession, String sql) {
        List<String> columns = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int[] rowCount = {0};
        dataBaseService.queryTableDataStreaming(chatSession, sql, row -> {
            if (rowCount[0] == 0) {
                columns.addAll(row.keySet());
                appendMarkdownTableHeader(sb, columns);
            }
            appendMarkdownTableDataRow(sb, columns, row);
            rowCount[0]++;
        });
        if (rowCount[0] == 0) {
            return "查询成功，结果为空（0 行）。";
        }
        return sb.toString();
    }

    private static void appendMarkdownTableHeader(StringBuilder sb, List<String> columns) {
        sb.append("| ");
        sb.append(String.join(" | ", columns));
        sb.append(" |\n|");
        sb.append(" --- |".repeat(columns.size()));
        sb.append("\n");
    }

    private static void appendMarkdownTableDataRow(StringBuilder sb, List<String> columns, Map<String, Object> row) {
        sb.append("| ");
        for (int j = 0; j < columns.size(); j++) {
            if (j > 0) {
                sb.append(" | ");
            }
            Object v = row.get(columns.get(j));
            sb.append(ObjectUtil.isNull(v) ? "" : escapePipe(String.valueOf(v)));
        }
        sb.append(" |\n");
    }

    private static String escapePipe(String cell) {
        return cell.replace("|", "\\|").replace("\n", " ");
    }
}
