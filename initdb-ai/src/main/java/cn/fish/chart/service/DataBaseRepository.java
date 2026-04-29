package cn.fish.chart.service;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.entity.Table;

import java.util.List;
import java.util.Map;

public interface DataBaseRepository {

    void test(ChatSession chatSession);

    void add(ChatSession chatSession);

    void remove(ChatSession chatSession);

    void removeAll();

    List<Table> queryTableList(String sessionId);

    Table queryTableSchema(String sessionId, String tableName);

    List<Map<String, Object>> queryTableData(String sessionId, String sql);
}
