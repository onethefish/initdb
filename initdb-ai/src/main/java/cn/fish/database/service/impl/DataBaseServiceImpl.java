package cn.fish.database.service.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.database.repository.DataBaseRepository;
import cn.fish.database.service.DataBaseService;
import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.fish.initDB.entity.Table;
import cn.fish.initDB.entity.TableColumn;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class DataBaseServiceImpl implements DataBaseService {


    private static final Cache<String, List<Table>> ALL_TABLE_CACHE = Caffeine.newBuilder()
                                                                              .expireAfterWrite(1, TimeUnit.MINUTES)
                                                                              .build();

    private static final Cache<String, Table> TABLE_SCHEMA_CACHE = Caffeine.newBuilder()
                                                                           .expireAfterWrite(1, TimeUnit.MINUTES)
                                                                           .build();
    private final DataBaseRepository dataBaseRepository;
    private final AgentDatasourceRepository agentDatasourceRepository;

    public DataBaseServiceImpl(
            DataBaseRepository dataBaseRepository,
            AgentDatasourceRepository agentDatasourceRepository) {
        this.dataBaseRepository = dataBaseRepository;
        this.agentDatasourceRepository = agentDatasourceRepository;
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
                String schema = conn.getSchema();
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
                String schema = conn.getSchema();
                Table table = new Table();
                table.setTableName(tableName);
                // 获取主键
                ResultSet metaDataPrimaryKeys = databaseMetaData.getPrimaryKeys(catalog, schema, tableName);
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
        List<Map<String, Object>> rows = new ArrayList<>();
        queryTableDataStreaming(chatSession, sql, rows::add);
        return rows;
    }

    @Override
    public void queryTableDataStreaming(ChatSession chatSession, String sql, Consumer<Map<String, Object>> rowConsumer) {
        DataSource dataSource = getDataSource(chatSession);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        ColumnMapRowMapper mapper = new ColumnMapRowMapper();
        int[] rowIndex = {0};
        jdbcTemplate.query(
                connection -> connection.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY),
                (RowCallbackHandler) rs -> rowConsumer.accept(mapper.mapRow(rs, rowIndex[0]++)));
    }

    private DataSource getDataSource(ChatSession chatSession) {
        String sessionId = chatSession.getSessionId();
        DataSource result = dataBaseRepository.get(sessionId);
        // 重新加载数据源
        if (result == null) {
            String datasourceId = chatSession.getDatasourceId();
            AgentDatasource byId = agentDatasourceRepository.getById(datasourceId);
            result = dataBaseRepository.add(sessionId, byId.getConnectionUrl(), byId.getUsername(), byId.getPassword());
        }
        return result;
    }
}
