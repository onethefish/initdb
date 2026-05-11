package cn.fish.initDB.workflow;

import cn.fish.initDB.workflow.node.DbDirectNl2SqlNode;
import cn.fish.initDB.workflow.node.DbDirectSqlGuardNode;
import cn.fish.initDB.workflow.node.DbDirectTableCatalogNode;
import cn.fish.initDB.workflow.node.DbIntentClassificationNode;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 父图 state 中 {@link #BUNDLE_STATE_KEY}（{@code db_bundle}）的读写助手。
 * 本类维护的 bundle 子键：{@link #DB_BUNDLE_KEY_SESSION_ID}、{@link #DB_BUNDLE_KEY_DIRECT_ANSWER}；
 * 其余子键由各写入节点上的 {@code DB_BUNDLE_KEY_*} 声明。
 * <p>
 * 旧 checkpoint：无 {@code db_*} 子键或仍为顶层直连字段时，{@link #readCopy(OverAllState)} 会合并进副本。
 */
public final class DbWorkflowBundle {

    /** 父图 state 顶层键：直连与路由等字段的嵌套 Map。 */
    public static final String BUNDLE_STATE_KEY = "db_bundle";

    public static final String DB_BUNDLE_KEY_SESSION_ID = "db_session_id";
    public static final String DB_BUNDLE_KEY_DIRECT_ANSWER = "db_direct_answer";

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
        state.value(BUNDLE_STATE_KEY)
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
        overlayLegacyTopLevel(state, b, DB_BUNDLE_KEY_SESSION_ID,
                DB_BUNDLE_KEY_SESSION_ID, LEGACY_TOP_SESSION_ID);
        overlayLegacyTopLevel(state, b, DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE,
                DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE);
        overlayLegacyTopLevel(state, b, DbDirectNl2SqlNode.DB_BUNDLE_KEY_GENERATED_SQL,
                DbDirectNl2SqlNode.DB_BUNDLE_KEY_GENERATED_SQL, LEGACY_TOP_GENERATED_SQL);
        overlayLegacyTopLevel(state, b, DbDirectSqlGuardNode.DB_BUNDLE_KEY_SQL_GUARD,
                DbDirectSqlGuardNode.DB_BUNDLE_KEY_SQL_GUARD, LEGACY_TOP_SQL_GUARD_OK);
        overlayLegacyTopLevel(state, b, DB_BUNDLE_KEY_DIRECT_ANSWER,
                DB_BUNDLE_KEY_DIRECT_ANSWER, LEGACY_TOP_DIRECT_ANSWER);
        overlayLegacyTopLevel(state, b, DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG_JSON,
                DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG_JSON, LEGACY_TOP_TABLE_CATALOG_JSON);
        overlayLegacyTopLevel(state, b, DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG,
                DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG, LEGACY_TOP_CATALOG_OK, LEGACY_TOP_DB_CATALOG_OK);
        normalizeDbRouteValue(b);
        return b;
    }

    private static void aliasLegacyBundleKeys(Map<String, Object> b) {
        copyAliasIfAbsent(b, LEGACY_BUNDLE_SESSION_ID, DB_BUNDLE_KEY_SESSION_ID);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_TABLE_CATALOG_JSON, DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG_JSON);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_CATALOG_OK, DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_DB_CATALOG_OK, DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_GENERATED_SQL, DbDirectNl2SqlNode.DB_BUNDLE_KEY_GENERATED_SQL);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_SQL_GUARD_OK, DbDirectSqlGuardNode.DB_BUNDLE_KEY_SQL_GUARD);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_DB_SQL_GUARD_OK, DbDirectSqlGuardNode.DB_BUNDLE_KEY_SQL_GUARD);
        copyAliasIfAbsent(b, LEGACY_BUNDLE_DIRECT_ANSWER, DB_BUNDLE_KEY_DIRECT_ANSWER);
    }

    private static void copyAliasIfAbsent(Map<String, Object> b, String legacyKey, String canonicalKey) {
        if (!b.containsKey(canonicalKey) && b.containsKey(legacyKey)) {
            b.put(canonicalKey, b.get(legacyKey));
        }
    }

    /**
     * 将 {@link DbIntentClassificationNode#DB_BUNDLE_KEY_ROUTE} 规范为 {@link Boolean}：
     * 新数据为 {@code true}/{@code false}；旧 checkpoint 中非布尔仅识别直连别名 {@code "direct_data"}（及空串等）迁移为布尔。
     */
    private static void normalizeDbRouteValue(Map<String, Object> b) {
        Object v = b.get(DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE);
        if (v instanceof Boolean) {
            return;
        }
        if (ObjectUtil.isNull(v)) {
            b.put(DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
            return;
        }
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) {
            b.put(DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
            return;
        }
        if ("direct_data".equalsIgnoreCase(s)) {
            b.put(DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE, Boolean.TRUE);
        } else {
            b.put(DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
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

    /** 单顶层键 {@link #BUNDLE_STATE_KEY} 的 state 更新。 */
    public static Map<String, Object> writeBundle(OverAllState state, Consumer<Map<String, Object>> mutator) {
        Map<String, Object> b = readCopy(state);
        mutator.accept(b);
        stripLegacyBundleKeys(b);
        Map<String, Object> out = new HashMap<>(2);
        out.put(BUNDLE_STATE_KEY, b);
        return out;
    }

    /**
     * 与 {@link #writeBundle} 一致：去掉旧 wire 子键，避免 checkpoint 与新键长期并存
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
        b.put(DB_BUNDLE_KEY_SESSION_ID, sessionId);
        b.put(DbIntentClassificationNode.DB_BUNDLE_KEY_ROUTE, Boolean.FALSE);
        b.put(DB_BUNDLE_KEY_DIRECT_ANSWER, "");
        b.put(DbDirectNl2SqlNode.DB_BUNDLE_KEY_GENERATED_SQL, "");
        b.put(DbDirectSqlGuardNode.DB_BUNDLE_KEY_SQL_GUARD, Boolean.FALSE);
        b.put(DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG_JSON, "[]");
        b.put(DbDirectTableCatalogNode.DB_BUNDLE_KEY_TABLE_CATALOG, Boolean.FALSE);
        return b;
    }

    /**
     * 流式兜底读取 {@link #DB_BUNDLE_KEY_DIRECT_ANSWER}：优先 bundle，其次旧版顶层。
     */
    public static Optional<Object> directAnswerFrom(NodeOutput nodeOutput) {
        OverAllState st = nodeOutput.state();
        Map<String, Object> b = readCopy(st);
        Object v = b.get(DB_BUNDLE_KEY_DIRECT_ANSWER);
        if (ObjectUtil.isNotNull(v)) {
            return Optional.of(v);
        }
        Optional<Object> o = st.value(DB_BUNDLE_KEY_DIRECT_ANSWER);
        if (o.isPresent()) {
            return o;
        }
        return st.value(LEGACY_TOP_DIRECT_ANSWER);
    }
}
