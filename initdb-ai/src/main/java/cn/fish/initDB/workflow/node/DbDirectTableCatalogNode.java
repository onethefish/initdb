package cn.fish.initDB.workflow.node;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.database.service.DataBaseService;
import cn.fish.initDB.entity.Table;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直连链路前置：按会话数据源拉取表清单（物理表名 + 注释），供 {@link DbDirectNl2SqlNode} 将用户中文称呼映射到真实 {@code FROM} 表名。
 */
@Slf4j
@Component
public class DbDirectTableCatalogNode implements NodeAction {

    // StateGraph 节点 id，勿改字符串以免破坏 checkpoint / 流式帧匹配
    public static final String GRAPH_NODE_ID = "db_direct_table_catalog";

    /** {@link cn.fish.initDB.workflow.DbWorkflowBundle#BUNDLE_STATE_KEY} 内：表清单 JSON（表名 + 注释）。 */
    public static final String DB_BUNDLE_KEY_TABLE_CATALOG_JSON = "db_table_catalog_json";

    /** 同 bundle 内：表清单是否已就绪（布尔）。 */
    public static final String DB_BUNDLE_KEY_TABLE_CATALOG = "db_table_catalog";

    private final DataBaseService dataBaseService;
    private final ChatSessionRepository chatSessionRepository;

    public DbDirectTableCatalogNode(DataBaseService dataBaseService, ChatSessionRepository chatSessionRepository) {
        this.dataBaseService = dataBaseService;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Map<String, Object> bundle = DbWorkflowBundle.readCopy(state);
        String sessionId = DbWorkflowBundle.bundleString(bundle, DbWorkflowBundle.DB_BUNDLE_KEY_SESSION_ID, "");
        if (StrUtil.isBlank(sessionId)) {
            return fail(state, "缺少会话标识，无法加载表清单。");
        }
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        if (ObjectUtil.isNull(chatSession)) {
            return fail(state, "未找到会话，请先连接数据库。");
        }
        try {
            List<Table> tables = dataBaseService.queryTableList(chatSession);
            JSONArray arr = new JSONArray();
            if (ObjectUtil.isNotNull(tables)) {
                for (Table t : tables) {
                    if (ObjectUtil.isNull(t) || StrUtil.isBlank(t.getTableName())) {
                        continue;
                    }
                    JSONObject o = new JSONObject(new LinkedHashMap<>(4));
                    o.put("tableName", t.getTableName());
                    o.put("remarks", StrUtil.nullToEmpty(t.getRemarks()));
                    arr.add(o);
                }
            }
            String json = JSON.toJSONString(arr);
            log.info("db direct table_catalog tables={}", arr.size());
            return DbWorkflowBundle.writeBundle(state, b -> {
                b.put(DB_BUNDLE_KEY_TABLE_CATALOG_JSON, json);
                b.put(DB_BUNDLE_KEY_TABLE_CATALOG, Boolean.TRUE);
            });
        } catch (Exception e) {
            log.error("db direct table_catalog failed", e);
            return fail(state, "加载表清单失败：" + e.getMessage());
        }
    }

    private static Map<String, Object> fail(OverAllState state, String msg) {
        String md = "## 提示\n\n" + msg;
        return DbWorkflowBundle.writeBundle(state, b -> {
            b.put(DB_BUNDLE_KEY_TABLE_CATALOG_JSON, "[]");
            b.put(DB_BUNDLE_KEY_TABLE_CATALOG, Boolean.FALSE);
            b.put(DbWorkflowBundle.DB_BUNDLE_KEY_DIRECT_ANSWER, md);
        });
    }
}
