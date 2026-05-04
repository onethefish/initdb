package cn.fish.database.repository.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.database.repository.DataBaseRepository;
import cn.fish.initDB.entity.Table;
import cn.fish.initDB.entity.TableColumn;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
public class DataBaseRepositoryImpl implements DataBaseRepository {

    private static final Cache<String, HikariDataSource> DATA_SOURCE_CACHE = Caffeine.newBuilder()
                                                                                     .maximumSize(128) // 最大支持128个会话
                                                                                     .build();

    private static final Cache<String, List<Table>> ALL_TABLE_CACHE = Caffeine.newBuilder()
                                                                              .expireAfterWrite(1, TimeUnit.MINUTES)
                                                                              .build();

    private static final Cache<String, Table> TABLE_SCHEMA_CACHE = Caffeine.newBuilder()
                                                                           .expireAfterWrite(1, TimeUnit.MINUTES)
                                                                           .build();

    @NotNull
    private static HikariConfig createHikariConfig(String url, String username, String password) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(url);
        if (StrUtil.isNotBlank(username)) {
            hikariConfig.setUsername(username);
        }
        if (StrUtil.isNotBlank(password)) {
            hikariConfig.setPassword(password);
        }
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        return hikariConfig;
    }

    @NotNull
    private static HikariDataSource createDataSource(ChatSession chatSession) {
        HikariConfig hikariConfig = createHikariConfig(chatSession.getUrl(), chatSession.getUsername(), chatSession.getPassword());
        return new HikariDataSource(hikariConfig);
    }

    private static void dataSourceClose(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.close();
        }
    }

    @Override
    public void test(String url, String username, String password) {
        HikariConfig hikariConfig = createHikariConfig(url, username, password);
        try (HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig)) {
            boolean running = hikariDataSource.isRunning();
        } catch (Exception e) {
            throw new CommonException("数据库连接失败" + e.getMessage(), e);
        } finally {
        }
    }

    @Override
    public void add(ChatSession chatSession) {
        HikariDataSource hikariDataSource = createDataSource(chatSession);
        DATA_SOURCE_CACHE.put(chatSession.getSessionId(), hikariDataSource);
    }

    @Override
    public void remove(ChatSession chatSession) {
        try {
            DataSource dataSource = getDataSource(chatSession);
            dataSourceClose(dataSource);
            DATA_SOURCE_CACHE.invalidate(chatSession.getSessionId());
        } catch (Exception ignored) {

        }
    }

    @Override
    public void removeAll() {
        DATA_SOURCE_CACHE.asMap().forEach((key, value) -> {
            dataSourceClose(value);
        });
        DATA_SOURCE_CACHE.invalidateAll();
    }

    @Override
    public List<Table> queryTableList(ChatSession chatSession) {
        String sessionId = chatSession.getSessionId();
        return ALL_TABLE_CACHE.get(sessionId, v -> {
            DataSource dataSource = getDataSource(chatSession);
            List<Table> tables = new ArrayList<>();
            try (Connection conn = dataSource.getConnection()) {
                String catalog = conn.getCatalog();//目录名称，一般都为空
                //schema = "%";//数据库名，对于mysql来说用通配符
                DatabaseMetaData dbmd = conn.getMetaData();
                String schema = chatSession.getSchemaName();//数据库名称
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
        });
    }

    @Override
    public Table queryTableSchema(ChatSession chatSession, String tableName) {
        String sessionId = chatSession.getSessionId();
        Table result = TABLE_SCHEMA_CACHE.get(sessionId + tableName, v -> {
            DataSource dataSource = getDataSource(chatSession);
            try (Connection conn = dataSource.getConnection()) {
                DatabaseMetaData databaseMetaData = conn.getMetaData();
                String catalog = conn.getCatalog();
                String schema = chatSession.getSchemaName();
                //            ArrayList<Table> resultList = new ArrayList();
                Table table = new Table();
                table.setTableName(tableName);
                // 获取主键
                ResultSet metaDataPrimaryKeys = databaseMetaData.getPrimaryKeys(catalog,schema, tableName);
                while (metaDataPrimaryKeys.next()) {
                    String column_name = metaDataPrimaryKeys.getString("COLUMN_NAME");
                    Integer key_seq = metaDataPrimaryKeys.getInt("KEY_SEQ");
                    table.addPrimaryKeysMap(key_seq, column_name);
                }
                // 获取索引
                ResultSet indexInfos = databaseMetaData.getIndexInfo(catalog, schema, tableName, false, true);
                int key_seq = 1;
                while (indexInfos.next()) {
                    String index_name = indexInfos.getString("INDEX_NAME");
                    String column_name = indexInfos.getString("COLUMN_NAME");
                    //                        Integer key_seq = indexInfos.getInt("SEQ_IN_INDEX");
                    key_seq++;
                    if (!"PRIMARY".equalsIgnoreCase(index_name)) {
                        table.setIndexMapMap(index_name, key_seq, column_name);
                    }
                }
                table.indexMapSort();
                // 获取表字段
                ResultSet columns = databaseMetaData.getColumns(catalog, schema, tableName, "%");
                while (columns.next()) {
                    TableColumn tableColumn = new TableColumn();
                    String column_name = columns.getString("COLUMN_NAME");
                    String type_name = columns.getString("TYPE_NAME");// 数据类型
                    String column_size = columns.getString("COLUMN_SIZE");// 长度
                    String decimal_digits = columns.getString("DECIMAL_DIGITS");// 精度
                    String column_def = columns.getString("COLUMN_DEF");// 默认值
                    boolean is_nullable = columns.getBoolean("IS_NULLABLE");
                    String remarks = columns.getString("REMARKS");// 注释
                    tableColumn.setColumnInfo(column_name, type_name, column_size, decimal_digits, column_def, is_nullable);
                    tableColumn.setRemarks(remarks);
                    table.addTableColumnMap(column_name, tableColumn);
                }
                table.primaryKeysSort();
                table.tableColumnSort();
                table.dealColumn();
                return table;
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return null;
            }
        });
        return result;
    }

    @Override
    public List<Map<String, Object>> queryTableData(ChatSession chatSession, String sql) {
        DataSource dataSource = getDataSource(chatSession);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return jdbcTemplate.queryForList(sql);
    }

    private DataSource getDataSource(ChatSession chatSession) {
        String sessionId = chatSession.getSessionId();
        HikariDataSource result = DATA_SOURCE_CACHE.get(sessionId, v -> createDataSource(chatSession));
        if (result == null) {
            throw new IllegalArgumentException("sessionId=" + sessionId + " not exist");
        }
        return result;
    }
}
