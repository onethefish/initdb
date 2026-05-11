package cn.fish.database.sql;

import cn.fish.chart.entity.ChatSession;
import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;

/**
 * 根据数据源配置或 JDBC URL 推断 {@link SqlDialect}。
 */
public final class SqlDialectResolver {

    private SqlDialectResolver() {
    }

    public static SqlDialect fromChatSession(ChatSession session, AgentDatasourceRepository repository) {
        if (ObjectUtil.isNull(session) || StrUtil.isBlank(session.getDatasourceId()) || ObjectUtil.isNull(repository)) {
            return SqlDialect.UNKNOWN;
        }
        AgentDatasource ds = repository.getById(session.getDatasourceId());
        if (ObjectUtil.isNull(ds)) {
            return SqlDialect.UNKNOWN;
        }
        return fromTypeAndUrl(ds.getType(), ds.getConnectionUrl());
    }

    public static SqlDialect fromTypeAndUrl(String type, String jdbcUrl) {
        SqlDialect byType = fromType(type);
        if (byType != SqlDialect.UNKNOWN) {
            return byType;
        }
        return fromJdbcUrl(jdbcUrl);
    }

    public static SqlDialect fromType(String type) {
        if (StrUtil.isBlank(type)) {
            return SqlDialect.UNKNOWN;
        }
        return switch (type.trim().toLowerCase()) {
            case "mysql" -> SqlDialect.MYSQL;
            case "mariadb" -> SqlDialect.MARIADB;
            case "postgresql", "postgres" -> SqlDialect.POSTGRESQL;
            case "h2" -> SqlDialect.H2;
            case "sqlite" -> SqlDialect.SQLITE;
            case "sqlserver", "mssql" -> SqlDialect.SQLSERVER;
            case "oracle" -> SqlDialect.ORACLE;
            default -> SqlDialect.UNKNOWN;
        };
    }

    public static SqlDialect fromJdbcUrl(String url) {
        if (StrUtil.isBlank(url)) {
            return SqlDialect.UNKNOWN;
        }
        String u = url.trim().toLowerCase();
        if (u.startsWith("jdbc:mysql:")) {
            return SqlDialect.MYSQL;
        }
        if (u.startsWith("jdbc:mariadb:")) {
            return SqlDialect.MARIADB;
        }
        if (u.startsWith("jdbc:postgresql:") || u.startsWith("jdbc:pgsql:")) {
            return SqlDialect.POSTGRESQL;
        }
        if (u.startsWith("jdbc:h2:")) {
            return SqlDialect.H2;
        }
        if (u.startsWith("jdbc:sqlite:")) {
            return SqlDialect.SQLITE;
        }
        if (u.startsWith("jdbc:sqlserver:") || u.startsWith("jdbc:microsoft:sqlserver:")) {
            return SqlDialect.SQLSERVER;
        }
        if (u.startsWith("jdbc:oracle:")) {
            return SqlDialect.ORACLE;
        }
        return SqlDialect.UNKNOWN;
    }
}
