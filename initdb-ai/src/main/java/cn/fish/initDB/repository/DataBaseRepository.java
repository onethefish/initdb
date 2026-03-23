package cn.fish.initDB.repository;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.entity.Table;

import java.util.List;

public interface DataBaseRepository {

    void test(ChatSession chatSession);

    void add(ChatSession chatSession);

    void remove(ChatSession chatSession);

    void removeAll();

    List<Table> queryTableList(String sessionId);

    Table queryTableSchema(String sessionId, String tableName);
}
