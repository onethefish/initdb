package cn.fish.database.sql;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.Fetch;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.Top;

/**
 * 使用 JSQLParser 解析 {@code SELECT}，在语句尚未带行数上限时按方言追加限制。
 * <p>
 * 解析失败时回退为简单的 {@code LIMIT n} 拼接（与历史行为一致）。
 */
@Slf4j
public final class SelectSqlRowLimiter {

    private SelectSqlRowLimiter() {
    }

    /**
     * 若整句为 {@code SELECT} 且 AST 上尚无行数上限，则按方言写入上限；否则返回原始 SQL。
     *
     * @param sql      原始 SQL（可含末尾分号）
     * @param maxRows  最大行数（应 ≥ 1）
     * @param dialect  目标库方言
     * @return 可能已改写后的 SQL（尽量保留是否以分号结尾）
     */
    public static String ensureSelectRowLimit(String sql, int maxRows, SqlDialect dialect) {
        if (StrUtil.isBlank(sql) || maxRows < 1) {
            return sql;
        }
        String trimmed = sql.trim();
        boolean trailingSemi = trimmed.endsWith(";");
        String withoutSemi = trailingSemi ? trimmed.substring(0, trimmed.length() - 1).trim() : trimmed;

        try {
            Statement stmt = CCJSqlParserUtil.parse(withoutSemi);
            if (!(stmt instanceof Select select)) {
                return sql;
            }
            if (hasExistingRowCap(select)) {
                return sql;
            }
            applyRowCap(select, maxRows, dialect);
            String rebuilt = stmt.toString();
            return trailingSemi ? rebuilt + ";" : rebuilt;
        } catch (JSQLParserException e) {
            log.warn("Unable to parse SQL for row limit, using string fallback: {}", e.getMessage());
            return fallbackAppendLimit(sql, maxRows);
        }
    }

    private static boolean hasExistingRowCap(Select select) {
        if (ObjectUtil.isNotNull(select.getLimit()) || ObjectUtil.isNotNull(select.getFetch()) || ObjectUtil.isNotNull(select.getLimitBy())) {
            return true;
        }
        if (select instanceof PlainSelect plain) {
            return ObjectUtil.isNotNull(plain.getTop()) || ObjectUtil.isNotNull(plain.getFirst());
        }
        return false;
    }

    private static void applyRowCap(Select select, int n, SqlDialect dialect) {
        LongValue rowExpr = new LongValue(n);
        switch (dialect) {
            case SQLSERVER:
                if (select instanceof PlainSelect plain) {
                    plain.setTop(new Top().withExpression(rowExpr));
                } else {
                    // 非简单 SELECT 时仍使用 LIMIT 节点（部分场景或需人工调整 T-SQL）
                    select.setLimit(new Limit().withRowCount(rowExpr));
                }
                break;
            case ORACLE:
                select.setFetch(new Fetch().withRowCount(n));
                break;
            default:
                select.setLimit(new Limit().withRowCount(rowExpr));
        }
    }

    /**
     * 与原先字符串检测逻辑等价，仅在解析失败时使用。
     */
    private static String fallbackAppendLimit(String sql, int maxRows) {
        String q = sql.trim();
        String lower = q.toLowerCase();
        if (lower.contains(" limit ") || lower.contains("\nlimit ")) {
            return sql;
        }
        if (q.endsWith(";")) {
            q = q.substring(0, q.length() - 1);
        }
        return q + " LIMIT " + maxRows;
    }
}
