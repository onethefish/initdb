package cn.fish.database.service;

import cn.fish.chart.entity.ChatSession;
import cn.fish.initDB.entity.Table;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public interface DataBaseService {



    List<Table> queryTableList(ChatSession chatSession);

    Table queryTableSchema(ChatSession chatSession, String tableName);

    List<Map<String, Object>> queryTableData(ChatSession chatSession, String sql);

    void queryTableDataStreaming(ChatSession chatSession, String sql, Consumer<Map<String, Object>> rowConsumer);
}
