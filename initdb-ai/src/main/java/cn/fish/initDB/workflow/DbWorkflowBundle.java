package cn.fish.initDB.workflow;

import cn.fish.initDB.constants.InitDBConstants;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 父图 state 中 {@link InitDBConstants#STATE_KEY_DB_BUNDLE} 的读写助手。
 * 内层仍使用 {@link InitDBConstants#STATE_KEY_SESSION_ID} 等子键，便于节点与条件边复用常量名。
 * <p>
 * 旧 checkpoint 若仍为顶层直连字段，{@link #readCopy(OverAllState)} 会尽力合并进副本，便于迁移。
 */
public final class DbWorkflowBundle {

    private DbWorkflowBundle() {
    }

    /**
     * 读取当前 bundle 的可变副本（浅拷贝 Map）；缺省子键时回填旧版顶层 state。
     */
    public static Map<String, Object> readCopy(OverAllState state) {
        Map<String, Object> b = new LinkedHashMap<>(8);
        state.value(InitDBConstants.STATE_KEY_DB_BUNDLE)
             .filter(Map.class::isInstance)
             .ifPresent(raw -> {
                 Map<?, ?> src = (Map<?, ?>) raw;
                 for (Map.Entry<?, ?> e : src.entrySet()) {
                     if (e.getKey() != null) {
                         b.put(String.valueOf(e.getKey()), e.getValue());
                     }
                 }
             });
        overlayLegacyTopLevel(state, b, InitDBConstants.STATE_KEY_SESSION_ID);
        overlayLegacyTopLevel(state, b, InitDBConstants.STATE_KEY_DB_ROUTE);
        overlayLegacyTopLevel(state, b, InitDBConstants.STATE_KEY_GENERATED_SQL);
        overlayLegacyTopLevel(state, b, InitDBConstants.STATE_KEY_SQL_GUARD_OK);
        overlayLegacyTopLevel(state, b, InitDBConstants.STATE_KEY_DIRECT_ANSWER);
        overlayLegacyTopLevel(state, b, InitDBConstants.STATE_KEY_DIRECT_TABLE_CATALOG_JSON);
        overlayLegacyTopLevel(state, b, InitDBConstants.STATE_KEY_DIRECT_CATALOG_OK);
        return b;
    }

    private static void overlayLegacyTopLevel(OverAllState state, Map<String, Object> b, String key) {
        if (b.containsKey(key)) {
            return;
        }
        state.value(key).ifPresent(v -> b.put(key, v));
    }

    public static String bundleString(Map<String, Object> bundle, String key, String defaultVal) {
        Object v = bundle.get(key);
        if (v == null) {
            return defaultVal;
        }
        return String.valueOf(v);
    }

    /** 单顶层键 {@code db_bundle} 的 state 更新。 */
    public static Map<String, Object> writeBundle(OverAllState state, Consumer<Map<String, Object>> mutator) {
        Map<String, Object> b = readCopy(state);
        mutator.accept(b);
        return Map.of(InitDBConstants.STATE_KEY_DB_BUNDLE, b);
    }

    public static Map<String, Object> newInitialBundle(String sessionId) {
        Map<String, Object> b = new LinkedHashMap<>(8);
        b.put(InitDBConstants.STATE_KEY_SESSION_ID, sessionId);
        b.put(InitDBConstants.STATE_KEY_DB_ROUTE, "");
        b.put(InitDBConstants.STATE_KEY_DIRECT_ANSWER, "");
        b.put(InitDBConstants.STATE_KEY_GENERATED_SQL, "");
        b.put(InitDBConstants.STATE_KEY_SQL_GUARD_OK, Boolean.FALSE);
        b.put(InitDBConstants.STATE_KEY_DIRECT_TABLE_CATALOG_JSON, "[]");
        b.put(InitDBConstants.STATE_KEY_DIRECT_CATALOG_OK, Boolean.FALSE);
        return b;
    }

    /**
     * 流式兜底读取 {@link InitDBConstants#STATE_KEY_DIRECT_ANSWER}：优先 bundle，其次旧版顶层。
     */
    public static Optional<Object> directAnswerFrom(NodeOutput nodeOutput) {
        OverAllState st = nodeOutput.state();
        Map<String, Object> b = readCopy(st);
        Object v = b.get(InitDBConstants.STATE_KEY_DIRECT_ANSWER);
        if (v != null) {
            return Optional.of(v);
        }
        return st.value(InitDBConstants.STATE_KEY_DIRECT_ANSWER);
    }
}
