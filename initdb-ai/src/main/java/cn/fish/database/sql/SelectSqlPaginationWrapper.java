package cn.fish.database.sql;

import cn.hutool.core.util.StrUtil;

/**
 * 将单条 SELECT 子查询包装为 COUNT 或分页查询（LIMIT/OFFSET 或方言等价语法）。
 * <p>
 * 分页边界（offset、limit）须由调用方校验为安全非负整数后，以字面量拼入 SQL（避免与业务 SQL 中占位符冲突）。
 */
public final class SelectSqlPaginationWrapper {

    private SelectSqlPaginationWrapper() {
    }

    /**
     * 去掉末尾分号并 trim，供包装为子查询。
     */
    public static String stripTrailingSemicolon(String sql) {
        if (StrUtil.isBlank(sql)) {
            return "";
        }
        String t = sql.trim();
        if (t.endsWith(";")) {
            t = t.substring(0, t.length() - 1).trim();
        }
        return t;
    }

    /**
     * {@code SELECT COUNT(1) FROM ( 用户 SQL ) _initdb_cnt}
     */
    public static String wrapAsCountSubquery(String innerSelectWithoutTrailingSemicolon) {
        return "SELECT COUNT(1) FROM (" + innerSelectWithoutTrailingSemicolon + ") _initdb_cnt";
    }

    /**
     * 分页：从 offset 起取 limit 行（0-based offset）。
     */
    public static String wrapAsPagedSubquery(
            String innerSelectWithoutTrailingSemicolon,
            SqlDialect dialect,
            long offset,
            long limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        String inner = innerSelectWithoutTrailingSemicolon;
        return switch (dialect) {
            case SQLSERVER -> "SELECT * FROM (" + inner + ") _initdb_p ORDER BY (SELECT NULL) OFFSET "
                    + offset + " ROWS FETCH NEXT " + limit + " ROWS ONLY";
            case ORACLE -> "SELECT * FROM (" + inner + ") _initdb_p OFFSET " + offset + " ROWS FETCH NEXT "
                    + limit + " ROWS ONLY";
            case MYSQL, MARIADB, POSTGRESQL, H2, SQLITE, UNKNOWN -> "SELECT * FROM (" + inner + ") _initdb_p LIMIT "
                    + limit + " OFFSET " + offset;
        };
    }
}
