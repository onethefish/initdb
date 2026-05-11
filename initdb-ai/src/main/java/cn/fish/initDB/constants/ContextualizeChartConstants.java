package cn.fish.initDB.constants;

/**
 * 与 DB {@code StateGraph} 主链路无直接绑定的辅助常量：问句补全（contextualize）裁剪阈值、Chart 摘要/标题展示用截断后缀等。
 * 工作流图、checkpoint 与 NDJSON 流协议见 {@link WorkflowConstants}。
 */
public final class ContextualizeChartConstants {

    private ContextualizeChartConstants() {
    }

    /** Chart 压缩与标题 snippet 共用的截断后缀（与前端展示一致） */
    public static final String CHART_SUMMARY_TRUNCATED_SUFFIX = "\n...[truncated]";

    public static final int CONTEXTUALIZE_MAX_HISTORY_CHARS = 4_000;
    public static final int CONTEXTUALIZE_MAX_PRIOR_MESSAGES = 20;
    public static final int CONTEXTUALIZE_BODY_MAX_CHARS = 2_000;
}
