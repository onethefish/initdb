package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.util.NodeOutputUtil;
import cn.fish.web.exception.CommonException;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Collection;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

    private final ReactAgent reactAgent;
    private final BaseCheckpointSaver baseCheckpointSaver;

    public DBAgentServiceImpl(ReactAgent reactAgent, BaseCheckpointSaver baseCheckpointSaver) {
        this.reactAgent = reactAgent;
        this.baseCheckpointSaver = baseCheckpointSaver;
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
        RunnableConfig config = RunnableConfig.builder()
                                              .threadId(sessionId)
                                              .mergeReasoningContent(true)
                                              .build();
        Flux<NodeOutput> stream = reactAgent.stream(chatRequest.getMessage(), config);
        // todo AI 乱改的 实际上用不了等一个大神
        return stream.filter(nodeOutput -> {
                         // 排除 START 和 END 节点
                         if (nodeOutput.isSTART() || nodeOutput.isEND()) {
                             return false;
                         }
                         // 只保留 StreamingOutput 类型（实时的流式输出）
                         return nodeOutput instanceof StreamingOutput;
                     })
                     .map(nodeOutput -> {
                         StreamingOutput streamingOutput = (StreamingOutput) nodeOutput;
                         return streamingOutput.chunk();
                     })
                     .filter(chunk -> chunk != null && !chunk.trim().isEmpty())
                     .map(NodeOutputUtil::getHtml);
    }

    @Override
    @McpTool(description = "获取会话中的数据库相关信息")
    public String getDBChart(@McpToolParam(description = "message") String message, @McpToolParam(description = "Session id") String sessionId) {
        return chat(new ChatRequest(message, sessionId)).getResponse();
    }


}
