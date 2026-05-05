package cn.fish.database.service;

import cn.fish.chart.entity.ChatSession;
import cn.fish.initDB.entity.Table;

import java.util.List;
import java.util.Map;

public interface DataBaseService {



    List<Table> queryTableList(ChatSession chatSession);

    Table queryTableSchema(ChatSession chatSession, String tableName);

    List<Map<String, Object>> queryTableData(ChatSession chatSession, String sql);
}
