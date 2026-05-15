package cn.fish.database.service;

import cn.fish.chart.entity.ChatSession;
import cn.fish.initDB.entity.Table;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface DataBaseService {



    List<Table> queryTableList(ChatSession chatSession);

    /**
     * 直连 NL2SQL 等场景使用的表清单 JSON 数组字符串（仅含 tableName、remarks），与 {@link #queryTableList} 同源；
     * 带进程内缓存，避免同会话多次直连时重复构建大 JSON。
     */
    String queryTableCatalogJson(ChatSession chatSession);

    Table queryTableSchema(ChatSession chatSession, String tableName);

    List<Map<String, Object>> queryTableData(ChatSession chatSession, String sql);

    void queryTableDataStreaming(ChatSession chatSession, String sql, Consumer<Map<String, Object>> rowConsumer);

    /**
     * 按会话清除表清单与表结构缓存（删除会话或重连数据源后调用）。
     */
    void invalidateMetadataCache(String sessionId);

    /** 清除全部元数据缓存（如清空所有会话）。 */
    void invalidateAllMetadataCaches();
}
