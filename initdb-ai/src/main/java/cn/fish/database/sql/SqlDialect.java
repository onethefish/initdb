package cn.fish.database.sql;

/**
 * 与 {@link SelectSqlRowLimiter} 配合使用的数据库方言，用于选择行数限制语法。
 */
public enum SqlDialect {
    MYSQL,
    MARIADB,
    POSTGRESQL,
    H2,
    SQLITE,
    SQLSERVER,
    ORACLE,
    /** 无法识别时按多数库可用的 {@code LIMIT} 处理 */
    UNKNOWN
}
