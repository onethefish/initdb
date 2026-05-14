package cn.fish.common.savers;

/**
 * 在 {@link com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver#release} 仅按单条 {@code threadId} 释放的基础上，
 * 按「业务会话 id」释放该会话在 saver 中的全部相关状态（例如主线程 + 子图线程）。
 * <p>
 * 由具体存储实现决定是否识别子线程命名规则；未实现的 saver 仍可对同一 bean 调用
 * {@code release(RunnableConfig.threadId(sessionId))} 作为回退。
 */
@FunctionalInterface
public interface CheckpointSessionTreeReleasable {

    /**
     * 释放与 {@code sessionId} 相关的全部 checkpoint 线程（含实现已知的相关 threadId）。
     *
     * @param sessionId 业务侧会话 id，非空
     */
    void releaseSessionTree(String sessionId) throws Exception;
}
