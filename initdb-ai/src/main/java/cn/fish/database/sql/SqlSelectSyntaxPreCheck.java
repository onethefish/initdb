package cn.fish.database.sql;

import cn.hutool.core.util.StrUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.Statements;
import net.sf.jsqlparser.statement.select.Select;

import java.util.Optional;

/**
 * 使用 JSqlParser 对「单条只读查询」做语法级预检，供 {@code sql_check} 在调用 LLM 前短路。
 */
public final class SqlSelectSyntaxPreCheck {

    /** 与 {@code prompts/query_sql_check.txt} 中约定一致，供 {@code verdict.contains("校验成功")} 判断 */
    public static final String SUCCESS_VERDICT = "校验成功";

    private SqlSelectSyntaxPreCheck() {
    }

    /**
     * @param rawSql      原始 SQL（可含末尾分号）
     * @param maxSqlChars 最大允许字符数
     * @return 有值表示语法预检已失败，可直接作为工具返回值；empty 表示解析通过且为单条 SELECT
     */
    public static Optional<String> tryParseFailureVerdict(String rawSql, int maxSqlChars) {
        if (StrUtil.isBlank(rawSql)) {
            return Optional.of("校验失败: SQL 为空。");
        }
        String trimmed = rawSql.trim();
        int cap = Math.max(1_000, maxSqlChars);
        if (trimmed.length() > cap) {
            return Optional.of("校验失败: SQL 过长（超过 " + cap + " 字符），已拒绝解析。");
        }
        boolean trailingSemi = trimmed.endsWith(";");
        String withoutSemi = trailingSemi ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;
        if (StrUtil.isBlank(withoutSemi)) {
            return Optional.of("校验失败: SQL 为空。");
        }
        try {
            Statements statements = CCJSqlParserUtil.parseStatements(withoutSemi);
            if (statements.size() != 1) {
                return Optional.of("校验失败: 仅支持单条 SELECT/WITH 查询（检测到 " + statements.size() + " 条语句）。");
            }
            Statement stmt = statements.get(0);
            if (!(stmt instanceof Select)) {
                return Optional.of("校验失败: 仅允许 SELECT 查询（当前语句不是只读查询）。");
            }
            return Optional.empty();
        } catch (JSQLParserException e) {
            String msg = StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName());
            return Optional.of("校验失败: 语法无法解析 — " + msg);
        }
    }
}
