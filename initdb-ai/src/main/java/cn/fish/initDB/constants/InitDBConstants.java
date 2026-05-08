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
    public static final String DB_CHAT_WORKFLOW_BEAN = "dbChatWorkflowGraph";

    // --- Compiled graph / nodes ---
    public static final String GRAPH_NAME_DB_CHAT_WORKFLOW = "db_chat_workflow";
    public static final String NODE_DB_REACT = "db_react";
    public static final String NODE_DB_AGENT_INPUT_BRIDGE = "db_agent_input_bridge";

    /** 父/子图 checkpoint threadId 中用于还原业务 sessionId 的片段 */
    public static final String SUBGRAPH_THREAD_MARKER = "_subgraph_";

    // --- Graph state keys ---
    public static final String STANDALONE = "standalone";
    public static final String STATE_KEY_MESSAGES = "messages";

    // --- NDJSON chat stream（与 static/js/chat.js 对齐）---
    public static final String NDJSON_KEY_PART = "p";
    public static final String NDJSON_KEY_TEXT = "t";
    public static final String STREAM_PART_CONTEXTUALIZE = "contextualize";
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
