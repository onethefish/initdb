package cn.fish.initDB.constants;

/**
 * 与 DB {@code StateGraph} 主链路无直接绑定的辅助常量：问句补全（contextualize）裁剪阈值、Chart 摘要/标题展示用截断后缀等。
 * 图顶层 state 通道名见 {@link WorkflowConstants}；NDJSON {@code p} 取值见 {@link cn.fish.initDB.util.DbChatGraphStream}。
 */
public final class ContextualizeChartConstants {

    private ContextualizeChartConstants() {
    }

    /** Chart 压缩与标题 snippet 共用的截断后缀（与前端展示一致） */
    public static final String CHART_SUMMARY_TRUNCATED_SUFFIX = "\n...[truncated]";

    /** 摘要输入从末尾保留时，标明更早内容已省略的前缀 */
    public static final String CHART_SUMMARY_HEAD_OMITTED_PREFIX = "（更早对话已省略）\n";

    public static final int CONTEXTUALIZE_MAX_HISTORY_CHARS = 4_000;
    public static final int CONTEXTUALIZE_MAX_PRIOR_MESSAGES = 20;
    public static final int CONTEXTUALIZE_BODY_MAX_CHARS = 2_000;
}
