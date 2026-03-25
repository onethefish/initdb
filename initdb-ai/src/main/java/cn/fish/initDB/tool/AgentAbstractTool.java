package cn.fish.initDB.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Map;
import java.util.Optional;


public abstract class AgentAbstractTool {

    public void log(ToolContext toolContext) {

    }


    public String getSessionId(ToolContext toolContext) {
        Map<String, Object> context = toolContext.getContext();
        RunnableConfig runnableConfig = (RunnableConfig) context.get("_AGENT_CONFIG_");
        Optional<String> threadId = runnableConfig.threadId();
        return threadId.get();
    }
}
