package cn.fish.initDB.workflow;

import cn.fish.initDB.constants.WorkflowConstants;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 父图 state 中 {@link WorkflowConstants#STATE_KEY_DB_BUNDLE} 的读写助手。
 * 子键见 {@link WorkflowConstants} 中 {@code DB_BUNDLE_KEY_*}（wire 统一 {@code db_*}）。
 * <p>
 * 旧 checkpoint：无 {@code db_*} 子键或仍为顶层直连字段时，{@link #readCopy(OverAllState)} 会合并进副本。
 */
public final class DbWorkflowBundle {

    private static final String LEGACY_TOP_SESSION_ID = "session_id";
    private static final String LEGACY_TOP_GENERATED_SQL = "generated_sql";
    private static final String LEGACY_TOP_SQL_GUARD_OK = "sql_guard_ok";
    private static final String LEGACY_TOP_DIRECT_ANSWER = "direct_answer";
    private static final String LEGACY_TOP_TABLE_CATALOG_JSON = "direct_table_catalog_json";
    private static final String LEGACY_TOP_CATALOG_OK = "direct_catalog_ok";
    private static final String LEGACY_TOP_DB_CATALOG_OK = "db_catalog_ok";

    private static final String LEGACY_BUNDLE_SESSION_ID = "session_id";
    private static final String LEGACY_BUNDLE_TABLE_CATALOG_JSON = "direct_table_catalog_json";
    private static final String LEGACY_BUNDLE_CATALOG_OK = "direct_catalog_ok";
    private static final String LEGACY_BUNDLE_GENERATED_SQL = "generated_sql";
    private static final String LEGACY_BUNDLE_SQL_GUARD_OK = "sql_guard_ok";
    private static final String LEGACY_BUNDLE_DB_SQL_GUARD_OK = "db_sql_guard_ok";
    private static final String LEGACY_BUNDLE_DB_CATALOG_OK = "db_catalog_ok";
    private static final String LEGACY_BUNDLE_DIRECT_ANSWER = "direct_answer";

    private DbWorkflowBundle() {
    }

    /**
     * 读取当前 bundle 的可变副本（浅拷贝 Map）；缺省子键时回填旧版顶层 state。
     */
    public static Map<String, Object> readCopy(OverAllState state) {
        Map<String, Object> b = new LinkedHashMap<>(8);
        state.value(WorkflowConstants.STATE_KEY_DB_BUNDLE)
             .filter(Map.class::isInstance)
             .ifPresent(raw -> {
                 Map<?, ?> src = (Map<?, ?>) raw;
                 for (Map.Entry<?, ?> e : src.entrySet()) {
                     if (ObjectUtil.isNotNull(e.getKey())) {
                         b.put(String.valueOf(e.getKey()), e.getValue());
                     }
                 }
             });
        aliasLegacyBundleKeys(b);
        overlayLegacyTopLevel(state, b, WorkflowConstants.DB_BUNDLE_KEY_SESSION_ID,
                WorkflowConstants.DB_BUNDLE_KEY_SESSION_ID, LEGACY_TOP_SESSION_ID);
        overlayLegacyTopLevel(state, b, WorkflowConstants.DB_BUNDLE_KEY_ROUTE,
                WorkflowConstants.DB_BUNDLE_KEY_ROUTE);
        overlayLegacyTopLevel(state, b, WorkflowConstants.DB_BUNDLE_KEY_GENERATED_SQL,
                WorkflowConstants.DB_BUNDLE_KEY_GENERATED_SQL, LEGACY_TOP_GENERATED_SQL);
        overlayLegacyTopLevel(state, b, WorkflowConstants.DB_BUNDLE_KEY_SQL_GUARD,
                WorkflowConstants.DB_BUNDLE_KEY_SQL_GUARD, LEGACY_TOP_SQL_GUARD_OK);
        overlayLegacyTopLevel(state, b, WorkflowConstants.DB_BUNDLE_KEY_DIRECT_ANSWER,
                WorkflowConstants.DB_BUNDLE_KEY_DIRECT_ANSWER, LEGACY_TOP_DIRECT_ANSWER);
        overlayLegacyTopLevel(state, b, WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG_JSON,
                WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG_JSON, LEGACY_TOP_TABLE_CATALOG_JSON);
        overlayLegacyTopLevel(state, b, WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG,
                WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG, LEGACY_TOP_CATALOG_OK, LEGACY_TOP_DB_CATALOG_OK);
        normalizeDbRouteValue(b);
        return b;
    }

    private static void aliasLegacyBundleKeys(Map<String, Object> b) {
        copyAliasIfAbsent(b, LEGACY_BUNDLE_SESSION_ID, WorkflowConstants.DB_BUNDLE_KEY_SESSION_ID);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_TABLE_CATALOG_JSON, WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG_JSON);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_CATALOG_OK, WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_DB_CATALOG_OK, WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_GENERATED_SQL, WorkflowConstants.DB_BUNDLE_KEY_GENERATED_SQL);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_SQL_GUARD_OK, WorkflowConstants.DB_BUNDLE_KEY_SQL_GUARD);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_DB_SQL_GUARD_OK, WorkflowConstants.DB_BUNDLE_KEY_SQL_GUARD);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_DIRECT_ANSWER, WorkflowConstants.DB_BUNDLE_KEY_DIRECT_ANSWER);
    }

    private static void copyAliasIfAbsent(Map<String, Object> b, String legacyKey, String canonicalKey) {
        if (!b.containsKey(canonicalKey) && b.containsKey(legacyKey)) {
            b.put(canonicalKey, b.get(legacyKey));
        }
    }

    /**
     * 将 {@link WorkflowConstants#DB_BUNDLE_KEY_ROUTE} 规范为 {@link Boolean}：
     * 新数据为 {@code true}/{@code false}；旧 checkpoint 中的 {@code "direct_data"}/{@code "react"} 或空串会迁移为布尔。
     */
    private static void normalizeDbRouteValue(Map<String, Object> b) {
        Object v = b.get(WorkflowConstants.DB_BUNDLE_KEY_ROUTE);
        if (v instanceof Boolean) {
            return;
        }
        if (ObjectUtil.isNull(v)) {
            b.put(WorkflowConstants.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
            return;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            b.put(WorkflowConstants.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
            return;
        }
        if ("direct_data".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) {
            b.put(WorkflowConstants.DB_BUNDLE_KEY_ROUTE, Boolean.TRUE);
        } else {
            b.put(WorkflowConstants.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
        }
    }

    /**
     * 若 bundle 副本尚无 {@code canonicalKey}，则从顶层 state 按 canonical 再按 {@code legacyTopLevelKeys} 顺序取值写入 bundle。
     */
    private static void overlayLegacyTopLevel(OverAllState state, Map<String, Object> b,
            String canonicalKey, String... legacyTopLevelKeys) {
        if (b.containsKey(canonicalKey)) {
            return;
        }
        for (String k : legacyTopLevelKeys) {
            Optional<Object> ov = state.value(k);
            if (ov.isPresent()) {
                b.put(canonicalKey, ov.get());
                return;
            }
        }
    }

    public static String bundleString(Map<String, Object> bundle, String key, String defaultVal) {
        Object v = bundle.get(key);
        if (ObjectUtil.isNull(v)) {
            return defaultVal;
        }
        return String.valueOf(v);
    }

    /** 单顶层键 {@code db_bundle} 的 state 更新。 */
    public static Map<String, Object> writeBundle(OverAllState state, Consumer<Map<String, Object>> mutator) {
        Map<String, Object> b = readCopy(state);
        mutator.accept(b);
        stripLegacyBundleKeys(b);
        Map<String, Object> out = new HashMap<>(2);
        out.put(WorkflowConstants.STATE_KEY_DB_BUNDLE, b);
        return out;
    }

    /**
     * 与 {@link #writeBundle} 一致：去掉旧 wire 子键，避免 checkpoint 与 {@code DB_BUNDLE_KEY_*} 长期并存
     *（例如 {@link cn.fish.initDB.workflow.node.DbDirectExecuteQueryNode} 手写 bundle 输出时调用）。
     */
    public static void stripLegacyBundleKeys(Map<String, Object> b) {
        b.remove(LEGACY_BUNDLE_SESSION_ID);
        b.remove(LEGACY_BUNDLE_TABLE_CATALOG_JSON);
        b.remove(LEGACY_BUNDLE_CATALOG_OK);
        b.remove(LEGACY_BUNDLE_GENERATED_SQL);
        b.remove(LEGACY_BUNDLE_SQL_GUARD_OK);
        b.remove(LEGACY_BUNDLE_DB_SQL_GUARD_OK);
        b.remove(LEGACY_BUNDLE_DB_CATALOG_OK);
        b.remove(LEGACY_BUNDLE_DIRECT_ANSWER);
    }

    public static Map<String, Object> newInitialBundle(String sessionId) {
        Map<String, Object> b = new LinkedHashMap<>(8);
        b.put(WorkflowConstants.DB_BUNDLE_KEY_SESSION_ID, sessionId);
        b.put(WorkflowConstants.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
        b.put(WorkflowConstants.DB_BUNDLE_KEY_DIRECT_ANSWER, "");
        b.put(WorkflowConstants.DB_BUNDLE_KEY_GENERATED_SQL, "");
        b.put(WorkflowConstants.DB_BUNDLE_KEY_SQL_GUARD, Boolean.FALSE);
        b.put(WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG_JSON, "[]");
        b.put(WorkflowConstants.DB_BUNDLE_KEY_TABLE_CATALOG, Boolean.FALSE);
        return b;
    }

    /**
     * 流式兜底读取 {@link WorkflowConstants#DB_BUNDLE_KEY_DIRECT_ANSWER}：优先 bundle，其次旧版顶层。
     */
    public static Optional<Object> directAnswerFrom(NodeOutput nodeOutput) {
        OverAllState st = nodeOutput.state();
        Map<String, Object> b = readCopy(st);
        Object v = b.get(WorkflowConstants.DB_BUNDLE_KEY_DIRECT_ANSWER);
        if (ObjectUtil.isNotNull(v)) {
            return Optional.of(v);
        }
        Optional<Object> o = st.value(WorkflowConstants.DB_BUNDLE_KEY_DIRECT_ANSWER);
        if (o.isPresent()) {
            return o;
        }
        return st.value(LEGACY_TOP_DIRECT_ANSWER);
    }
}
