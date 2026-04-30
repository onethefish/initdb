package cn.fish.database.repository;

import cn.fish.chart.entity.ChatSession;
import cn.fish.initDB.entity.Table;

import java.util.List;
import java.util.Map;

public interface DataBaseRepository {

    void test(ChatSession chatSession);

    void add(ChatSession chatSession);

    void remove(ChatSession chatSession);

    void removeAll();

    List<Table> queryTableList(ChatSession chatSession);

    Table queryTableSchema(ChatSession chatSession, String tableName);

    List<Map<String, Object>> queryTableData(ChatSession chatSession, String sql);
}
