package cn.fish.initDB.util;

import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 判断用户输入是否为「明确写出的查询 SQL」，与意图路由、问句补全跳过逻辑保持一致。
 */
public final class ExplicitSqlUserInput {

    /** 整条输入就是一个 Markdown SQL 代码块（可选 sql 语言标记） */
    private static final Pattern WHOLE_FENCED_SQL = Pattern.compile(
            "(?is)^\\s*```(?:sql)?\\s*([\\s\\S]*?)\\s*```\\s*$");

    /** 去掉行首块注释后，整段为 SELECT / WITH 查询（允许行尾分号、首尾空白） */
    private static final Pattern STANDALONE_SELECT_OR_WITH = Pattern.compile(
            "(?is)^(?:/\\*[\\s\\S]*?\\*/\\s*|--[^\\n]*\\n\\s*)*(with\\b|select\\b)[\\s\\S]*\\s*;?\\s*$");

    private ExplicitSqlUserInput() {
    }

    /**
     * ① 整条就是一个 ``` / ```sql 围栏且内部为 SELECT/WITH；② 或未围栏时整条（可带头注释）即一条 SELECT/WITH。
     */
    public static boolean matches(String raw) {
        if (!StringUtils.hasText(raw)) {
            return false;
        }
        String trimmed = raw.trim();
        Matcher fence = WHOLE_FENCED_SQL.matcher(trimmed);
        if (fence.matches()) {
            String inner = fence.group(1).trim();
            return isStandaloneSelectOrWithBody(inner);
        }
        return STANDALONE_SELECT_OR_WITH.matcher(trimmed).matches();
    }

    private static boolean isStandaloneSelectOrWithBody(String sqlBody) {
        if (!StringUtils.hasText(sqlBody)) {
            return false;
        }
        return STANDALONE_SELECT_OR_WITH.matcher(sqlBody).matches();
    }
}
