package cn.fish.initDB.constants;

/**
 * initDB 模块内集中定义的非提示词常量（Bean 名、图节点/状态键、流式协议、数值阈值等）。
 * 各调用模型处的提示词保留在对应类中。
 */
public final class InitDBConstants {

    private InitDBConstants() {
    }

    // --- Spring beans ---
    public static final String DB_REACT_AGENT_BEAN = "dbReactAgent";
    /** {@link com.alibaba.cloud.ai.graph.agent.ReactAgent} 展示名 */
    public static final String DB_REACT_AGENT_DISPLAY_NAME = "数据库智能体";
    public static final String DB_CHAT_WORKFLOW_BEAN = "dbChatWorkflowGraph";

    // --- Compiled graph / nodes ---
    public static final String GRAPH_NAME = "db_chat_workflow";
    public static final String NODE_DB_REACT = "db_react";
    public static final String NODE_DB_AGENT_INPUT_BRIDGE = "db_agent_input_bridge";

    /** 意图：走原 ReAct 对话 */
    public static final String NODE_DB_INTENT = "db_intent_classification";
    /** 直连查数：拉取当前库表清单（tableName + remarks） */
    public static final String NODE_DB_DIRECT_TABLE_CATALOG = "db_direct_table_catalog";
    /** 直连查数：生成 SQL */
    public static final String NODE_DB_DIRECT_NL2SQL = "db_direct_nl2sql";
    /** 直连查数：校验 SQL */
    public static final String NODE_DB_DIRECT_SQL_GUARD = "db_direct_sql_guard";
    /** 直连查数：执行并写入展示文本 */
    public static final String NODE_DB_DIRECT_EXECUTE = "db_direct_execute";

    /**
     * 直连与路由相关字段的嵌套命名空间（顶层仅此一槽，内含 {@link #STATE_KEY_SESSION_ID} 等子键）。
     */
    public static final String STATE_KEY_DB_BUNDLE = "db_bundle";

    /** 工作流分支：{@link #ROUTE_REACT_VALUE} 或 {@link #ROUTE_DIRECT_DATA_VALUE}（存于 {@link #STATE_KEY_DB_BUNDLE} 内） */
    public static final String STATE_KEY_DB_ROUTE = "db_route";
    /** 客户端会话 id，供直连节点解析 {@link cn.fish.chart.entity.ChatSession} */
    public static final String STATE_KEY_SESSION_ID = "session_id";
    /** 直连：JSON 数组，元素含 {@code tableName}、{@code remarks}，供 NL2SQL 约束 {@code FROM} */
    public static final String STATE_KEY_DIRECT_TABLE_CATALOG_JSON = "direct_table_catalog_json";
    /** 直连：表清单是否已成功加载（否时 direct_answer 含提示，条件边走向 END） */
    public static final String STATE_KEY_DIRECT_CATALOG_OK = "direct_catalog_ok";
    /** 直连链路生成的单条 SQL */
    public static final String STATE_KEY_GENERATED_SQL = "generated_sql";
    /** 直连：SQL 是否通过校验（供条件边读取） */
    public static final String STATE_KEY_SQL_GUARD_OK = "sql_guard_ok";
    /** 直连：用户可见答复（Markdown 等），聊天流会据此回传 */
    public static final String STATE_KEY_DIRECT_ANSWER = "direct_answer";
    /**
     * 直连执行节点嵌入的流（{@code Flux<GraphResponse<StreamingOutput>>}），由 graph-core 展开为 {@link com.alibaba.cloud.ai.graph.streaming.StreamingOutput}。
     * 保留在<strong>顶层</strong> state（不放入 {@link #STATE_KEY_DB_BUNDLE}），且在本节点返回的 {@link Map} 中须置于首项，以便 {@code getEmbedFlux} 稳定识别。
     */
    public static final String STATE_KEY_DIRECT_EXECUTE_STREAM = "direct_execute_stream";

    public static final String ROUTE_REACT_VALUE = "react";
    public static final String ROUTE_DIRECT_DATA_VALUE = "direct_data";

    public static final String SQL_GUARD_EDGE_OK = "sql_guard_ok";
    public static final String SQL_GUARD_EDGE_FAIL = "sql_guard_fail";

    /** 直连表清单条件边：成功 → NL2SQL */
    public static final String DIRECT_CATALOG_EDGE_OK = "direct_catalog_ok";
    /** 直连表清单条件边：失败 → END */
    public static final String DIRECT_CATALOG_EDGE_FAIL = "direct_catalog_fail";

    /**
     * 父/子图 checkpoint threadId 中用于还原业务 sessionId 的片段
     */
    public static final String SUBGRAPH_THREAD_MARKER = "_subgraph_";

    // --- Graph state keys ---
    public static final String STANDALONE = "standalone";
    public static final String STATE_KEY_MESSAGES = "messages";

    // --- NDJSON chat stream（与 static/js/chat.js 对齐）---
    public static final String NDJSON_KEY_PART = "p";
    public static final String NDJSON_KEY_TEXT = "t";
    public static final String STREAM_PART_CONTEXTUALIZE = "contextualize";
    /** 工作流可观测（与 {@link #STREAM_PART_ANSWER} 分离，前端展示为「思考过程」） */
    public static final String STREAM_PART_TRACE = "trace";
    public static final String STREAM_PART_ANSWER = "answer";

    public static final String CHAT_STREAM_JSON_SERIALIZE_FAILED =
            "{\"p\":\"answer\",\"t\":\"流封装失败\"}\n";

    // --- Question contextualize（展示与裁剪参数，非模型 system 提示词）---
    public static final String CONTEXTUALIZE_DISPLAY_PREFIX = "补全的会话：";
    public static final String CONTEXTUALIZE_DISPLAY_PREFIX_ASCII_COLON = "补全的会话:";
    public static final int CONTEXTUALIZE_MAX_HISTORY_CHARS = 4_000;
    public static final int CONTEXTUALIZE_MAX_PRIOR_MESSAGES = 20;
    public static final int CONTEXTUALIZE_BODY_MAX_CHARS = 2_000;

    // --- Chart checkpoint compress（阈值与截断标记，非总结提示词）---
    public static final int CHART_COMPRESS_MIN_MESSAGES = 12;
    public static final int CHART_KEEP_RECENT_MESSAGES = 6;
    public static final int CHART_MAX_CHARS_FOR_SUMMARY_INPUT = 14_000;

    public static final String CHART_SUMMARY_TRUNCATED_SUFFIX = "\n...[truncated]";

    /** 与前端默认会话名前缀一致；仅此类占位名称由 {@link cn.fish.initDB.event.listen.ChartEventListener} 自动覆盖 */
    public static final String CHAT_SESSION_AUTO_NAME_PLACEHOLDER_PREFIX = "新的对话";

    /** 送入标题模型的对话片段上限（字符） */
    public static final int CHAT_SESSION_TITLE_SNIPPET_MAX_CHARS = 1_800;

    /** 自动会话标题写入库前的最大长度（略小于列宽 255，并避免 UI 过长） */
    public static final int CHAT_SESSION_TITLE_RESULT_MAX_CHARS = 48;

    /**
     * 非占位会话名时，每隔多少次「对话流正常结束」（{@link cn.fish.initDB.event.ChartAutoSummarizeEvent}）才再次调用模型自动命名；
     * 占位名仍按首次有内容即命名。
     */
    public static final int CHART_SESSION_AUTO_TITLE_EVERY_N_STREAMS = 3;

    // --- Knowledge retrieval tool ---
    public static final int KNOWLEDGE_RETRIEVAL_DEFAULT_TOP_K = 1;

    // --- DB chat workflow compile ---
    public static final int DB_CHAT_WORKFLOW_RECURSION_LIMIT = 100;

    // --- DBAgentServiceImpl user-visible stream errors ---
    public static final String CHAT_STREAM_ERR_SESSION_NULL = "Sorry, an error occurred: sessionId is null";
    public static final String CHAT_STREAM_ERR_EMPTY_MODEL =
            "未收到模型的文本输出，请重试。若多次出现，请检查模型 API、配额及网络；若刚做过对话压缩，可新建会话再试。";
    public static final String CHAT_STREAM_ERR_EMPTY_FLUX_PREFIX = "模型流式通道无有效内容（API 无结果、内容审核或上下文异常等）。请稍后重试，或新建会话。 详情：";
    public static final String CHAT_STREAM_ERR_FAILED_PREFIX = "对话生成失败: ";
    public static final String CHAT_STREAM_ERR_SETUP_PREFIX = "对话启动失败: ";
}
