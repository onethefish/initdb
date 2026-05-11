package cn.fish.initDB.constants;

/**
 * DB 聊天图与 Spring AI / Chart 等模块<strong>共用的顶层 state 通道名</strong>。
 * <p>
 * 其它约定：父图 Bean 名见 {@link cn.fish.initDB.workflow.DBAgentStateGraphConfig#COMPILED_GRAPH_BEAN}；
 * ReAct Bean 与展示名见 {@link cn.fish.initDB.workflow.agent.DbReactAgentConfig}；
 * {@code db_bundle} 见 {@link cn.fish.initDB.workflow.DbWorkflowBundle#BUNDLE_STATE_KEY}；
 * 条件边 pathMap 的 key 与路由返回值见 {@link cn.fish.initDB.workflow.DBAgentStateGraphConfig}（与下一跳节点 id 或 {@link com.alibaba.cloud.ai.graph.StateGraph#END} 一致）；
 * 流式 NDJSON 的 {@code p} 取值见 {@link cn.fish.initDB.util.DbChatGraphStream}。
 */
public final class WorkflowConstants {

    private WorkflowConstants() {
    }

    /**
     * 图顶层 state：本轮用户问句的纯文本（可由 {@code /db/chat/contextualize} 补全后由服务写入）。
     * 意图分类等节点从此读取；走 ReAct 时由 {@link cn.fish.initDB.workflow.node.DbAgentInputBridgeNode} 转成 {@link #STATE_KEY_MESSAGES}。
     */
    public static final String STANDALONE = "standalone";

    /**
     * 图 state 中 Spring AI 对话消息列表，与 {@link com.alibaba.cloud.ai.graph.agent.ReactAgent} 子图约定一致。
     */
    public static final String STATE_KEY_MESSAGES = "messages";
}
