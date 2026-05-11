package cn.fish.initDB.workflow.agent.tool;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;

/**
 * DB Agent 工具基类。子图与父图共用 {@link com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver} 时，
 * graph-core 会把子图执行的 {@link RunnableConfig#threadId()} 变为 {@code {业务sessionId}_subgraph_{nodeId}}，
 * 工具侧需还原为业务会话 id 才能命中 {@link cn.fish.chart.repository.ChatSessionRepository}。
 */
public abstract class AgentAbstractTool {

    public void log(ToolContext toolContext) {
    }

    public String getSessionId(ToolContext toolContext) {
        Map<String, Object> context = toolContext.getContext();
        if (ObjectUtil.isNull(context)) {
            throw new IllegalStateException("ToolContext has no context map");
        }
        RunnableConfig runnableConfig = (RunnableConfig) context.get(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);
        if (ObjectUtil.isNull(runnableConfig)) {
            throw new IllegalStateException("ToolContext missing " + ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY);
        }
        String threadId = runnableConfig.threadId()
                .orElseThrow(() -> new IllegalStateException("RunnableConfig missing threadId for tool execution"));
        return stripSubGraphCheckpointThreadSuffix(threadId);
    }

    /**
     * 与 {@link com.alibaba.cloud.ai.graph.internal.node.ResumableSubGraphAction#subGraphId} 生成的后缀对齐：
     * {@code original + "_subgraph_" + nodeId}。
     */
    public static String stripSubGraphCheckpointThreadSuffix(String threadId) {
        if (StrUtil.isEmpty(threadId)) {
            return threadId;
        }
        int idx = threadId.indexOf("_subgraph_");
        if (idx > 0) {
            return threadId.substring(0, idx);
        }
        return threadId;
    }
}
