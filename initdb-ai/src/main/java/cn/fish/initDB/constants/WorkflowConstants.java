package cn.fish.initDB.constants;

/**
 * DB 聊天 {@link com.alibaba.cloud.ai.graph.StateGraph} 工作流相关常量：Spring Bean 名、图顶层 state 键、{@code db_bundle} 内子键（{@code DB_BUNDLE_KEY_*}）、条件边分支键、
 * 与 {@code CompiledGraph#stream} NDJSON 协议（{@code static/js/chat.js}）对齐的 {@code p} 取值。
 * 业务节点 id 见各 {@code NodeAction} 实现类上的 {@code GRAPH_NODE_ID}。
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
    }

    // --- Spring beans ---
    public static final String DB_REACT_AGENT_BEAN = "dbReactAgent";
    /** {@link com.alibaba.cloud.ai.graph.agent.ReactAgent} 展示名（子图配置与流式 trace 文案共用） */
    public static final String DB_REACT_AGENT_DISPLAY_NAME = "数据库智能体";
    public static final String DB_CHAT_WORKFLOW_BEAN = "dbChatWorkflowGraph";

    /** 直连与路由等字段的顶层嵌套命名空间 */
    public static final String STATE_KEY_DB_BUNDLE = "db_bundle";

    /**
     * {@link #STATE_KEY_DB_BUNDLE} 内子键，wire 字面量统一 {@code db_*} 前缀（与顶层 graph channel 区分）。
     */
    public static final String DB_BUNDLE_KEY_ROUTE = "db_route";
    public static final String DB_BUNDLE_KEY_SESSION_ID = "db_session_id";
    public static final String DB_BUNDLE_KEY_TABLE_CATALOG_JSON = "db_table_catalog_json";
    /** 表清单是否已就绪（布尔），与 {@link #DB_BUNDLE_KEY_TABLE_CATALOG_JSON} 成对；命名风格同 {@link #DB_BUNDLE_KEY_ROUTE}。 */
    public static final String DB_BUNDLE_KEY_TABLE_CATALOG = "db_table_catalog";
    public static final String DB_BUNDLE_KEY_GENERATED_SQL = "db_generated_sql";
    /** SQL 校验是否通过（布尔）；命名风格同 {@link #DB_BUNDLE_KEY_ROUTE}（{@code db_*} 名词，无 {@code _ok} 后缀）。 */
    public static final String DB_BUNDLE_KEY_SQL_GUARD = "db_sql_guard";
    public static final String DB_BUNDLE_KEY_DIRECT_ANSWER = "db_direct_answer";
    /** 顶层 state：直连执行节点嵌入的流，须置于节点返回 Map 首项 */
    public static final String STATE_KEY_DIRECT_EXECUTE_STREAM = "direct_execute_stream";

    /**
     * 条件边 {@link com.alibaba.cloud.ai.graph.StateGraph#addConditionalEdges} 的 pathMap 分支 key（字符串），
     * 与 bundle 内 {@link #DB_BUNDLE_KEY_ROUTE}、{@link #DB_BUNDLE_KEY_SQL_GUARD}、{@link #DB_BUNDLE_KEY_TABLE_CATALOG} 的布尔语义一致。
     */
    public static final String GRAPH_BRANCH_TRUE = "true";
    public static final String GRAPH_BRANCH_FALSE = "false";

    /**
     * 图顶层 state 键：本轮用户问句的纯文本（可由 {@code /db/chat/contextualize} 补全后由 {@link cn.fish.initDB.service.impl.DBAgentServiceImpl} 写入）。
     * 意图分类等节点从此读取；走 ReAct 时由 {@link cn.fish.initDB.workflow.node.DbAgentInputBridgeNode} 转成 {@link #STATE_KEY_MESSAGES} 与默认输入。
     */
    public static final String STANDALONE = "standalone";
    /**
     * 图 state 中 Spring AI 对话消息列表（{@link org.springframework.ai.chat.messages.Message} 列表），与 {@link com.alibaba.cloud.ai.graph.agent.ReactAgent} 子图约定一致。
     * 桥接节点写入；ReAct 子图读写；Chart checkpoint 压缩（{@link cn.fish.initDB.event.listen.ChartEventListener}）也依赖此键。
     */
    public static final String STATE_KEY_MESSAGES = "messages";

    /** NDJSON 行中 {@code p}：正文增量 */
    public static final String STREAM_PART_ANSWER = "answer";
    /** NDJSON 行中 {@code p}：思考过程 / trace */
    public static final String STREAM_PART_TRACE = "trace";
}
