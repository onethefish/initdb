package cn.fish.common.savers;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

public class ChatMemorySaver extends MemorySaver implements CheckpointSessionTreeReleasable {

    @Override
    public void releaseSessionTree(String sessionId) throws Exception {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        release(RunnableConfig.builder().threadId(sessionId).build());
    }
}
