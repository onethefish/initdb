package cn.fish.initDB.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.util.NodeOutputUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collection;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

    private final ReactAgent reactAgent;
    private final BaseCheckpointSaver baseCheckpointSaver;
    private final ChatClient chatClient;

    public DBAgentServiceImpl(ReactAgent reactAgent, BaseCheckpointSaver baseCheckpointSaver, ChatClient chatClient) {
        this.reactAgent = reactAgent;
        this.baseCheckpointSaver = baseCheckpointSaver;
        this.chatClient = chatClient;
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            throw new CommonException("Sorry, an error occurred: sessionId is null");
        }
        try {
            RunnableConfig config = RunnableConfig.builder()
                                                  .threadId(sessionId)
                                                  .mergeReasoningContent(true)
                                                  .build();
            Collection<Checkpoint> list = baseCheckpointSaver.list(config);
            // 最大会话缓存数
            if (list.size() > 5) {
                baseCheckpointSaver.release(config);
            }
            NodeOutput result = reactAgent.invokeAndGetOutput(chatRequest.getMessage(), config).orElse(null);

            String response = NodeOutputUtil.extractResponse(result);
            return new ChatResponse(response, sessionId);
        } catch (Exception e) {
            throw new CommonException("Sorry, an error occurred: sessionId is null");
        }
    }

    @Override
    @SneakyThrows
    public Flux<String> chatStream(ChatRequest chatRequest) {
        log.info("Received chat request: {}", chatRequest.getMessage());
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            throw new CommonException("Sorry, an error occurred: sessionId is null");
        }

        ChatClient.ChatClientRequestSpec clientRequestSpec = chatClient.prompt()
                                                                       .user(chatRequest.getMessage())
                                                                       .advisors(memoryAdvisor -> memoryAdvisor
                                                                               .param(ChatMemory.CONVERSATION_ID, sessionId)
                                                                       );
        return clientRequestSpec.stream().content();
    }

    @Override
    @McpTool(description = "获取会话中的数据库相关信息")
    public String getDBChart(@McpToolParam(description = "message") String message, @McpToolParam(description = "Session id") String
            sessionId) {
        return chat(new ChatRequest(message, sessionId)).getResponse();
    }


}
