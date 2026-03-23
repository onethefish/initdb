package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.util.NodeOutputUtil;
import cn.hutool.core.util.IdUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

    @Autowired
    private ReactAgent reactAgent;

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        log.info("Received chat request: {}", chatRequest.getMessage());
        String sessionId = chatRequest.getSessionId();
        // todo
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = IdUtil.simpleUUID();
        }
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
            NodeOutput result = reactAgent.invokeAndGetOutput(chatRequest.getMessage(), config).orElse(null);
            String response = NodeOutputUtil.extractResponse(result);
            return new ChatResponse(response, sessionId, true);
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return new ChatResponse("Sorry, an error occurred: " + e.getMessage(), sessionId, false);
        }
    }
}
