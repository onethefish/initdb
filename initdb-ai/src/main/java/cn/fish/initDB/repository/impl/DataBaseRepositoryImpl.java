package cn.fish.initDB.repository.impl;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.entity.Table;
import cn.fish.initDB.repository.DataBaseRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

@Repository
public class DataBaseRepositoryImpl implements DataBaseRepository {

    private static final Cache<String, HikariDataSource> DATA_SOURCE_CACHE = Caffeine.newBuilder()
//                                                                                     .maximumSize(128) // 最大支持128个会话
                                                                                     .build();

    @Override
    public void test(ChatSession chatSession) {
        HikariConfig hikariConfig = createHikariConfig(chatSession);
        try (HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig)) {
            boolean running = hikariDataSource.isRunning();
        } finally {

        }
    }

    @NotNull
    private static HikariConfig createHikariConfig(ChatSession chatSession) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(chatSession.getUrl());
        hikariConfig.setUsername(chatSession.getUsername());
        hikariConfig.setPassword(chatSession.getPassword());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        return hikariConfig;
    }

    @Override
    public void add(ChatSession chatSession) {
        HikariConfig hikariConfig = createHikariConfig(chatSession);
        HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);
        DATA_SOURCE_CACHE.put(chatSession.getSessionId(), hikariDataSource);
    }

    @Override
    public List<Table> queryTableList(String sessionId) {
        DataSource dataSource = getDataSource(sessionId);
        List<Table> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String catalog = conn.getCatalog();//目录名称，一般都为空
            //schema = "%";//数据库名，对于mysql来说用通配符
            DatabaseMetaData dbmd = conn.getMetaData();
            String schema = dbmd.getUserName();//数据库名称
            // 表第一个字段为表名，第二个为表注释
            ResultSet tablesResultSet = dbmd.getTables(catalog, schema, "%", new String[]{"TABLE"});
            while (tablesResultSet.next()) {
                Table table = new Table();
                String table_name = tablesResultSet.getString("TABLE_NAME");  //表名
                String remarks = tablesResultSet.getString("REMARKS");       //表注释 不一定有
                table.setTableName(table_name);
                table.setRemarks(remarks);
                tables.add(table);
            }
            return tables;
        } catch (Exception ignored) {

        }
        return tables;
    }

    private DataSource getDataSource(String sessionId) {
        HikariDataSource result = DATA_SOURCE_CACHE.getIfPresent(sessionId);
        if (result == null) {
            throw new IllegalArgumentException("sessionId=" + sessionId + " not exist");
        }
        return result;
    }
}
