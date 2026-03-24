package cn.fish.initDB.service.impl;

import cn.fish.initDB.bo.AiChatAskBo;
import cn.fish.initDB.dto.BaiLianReqDto;
import cn.fish.initDB.dto.BaiLianRespDto;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.util.NodeOutputUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.OutputStream;
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
        log.info("Received chat request: {}", chatRequest.getMessage());
        String sessionId = chatRequest.getSessionId();
        // todo
        if (sessionId == null || sessionId.isEmpty()) {
            return new ChatResponse("Sorry, an error occurred: sessionId is null", null, false);
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
            return new ChatResponse(response, sessionId, true);
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return new ChatResponse("Sorry, an error occurred: " + e.getMessage(), sessionId, false);
        }
    }

    @Override
    @SneakyThrows
    // todo
    public Flux<String> chatStream(ChatRequest chatRequest) {
        log.info("Received chat request: {}", chatRequest.getMessage());
        String sessionId = chatRequest.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
        }
        RunnableConfig config = RunnableConfig.builder()
                                              .threadId(sessionId)
                                              .mergeReasoningContent(true)
                                              .build();
        Flux<NodeOutput> stream = reactAgent.stream(chatRequest.getMessage(), config);
        return stream.filter(nodeOutput -> !nodeOutput.isSTART() && !nodeOutput.isEND())
                     .map(NodeOutputUtil::extractResponse);
    }

    @Override
    public void ask(OutputStream os, AiChatAskBo bo) {
        BaiLianReqDto dto = new BaiLianReqDto();
        dto.setAskCode(bo.getChatCode())
        	.setPrompt(bo.getPrompt())
        	.setLastSessionId(bo.getLastSessionId());
      // todo  BaiLianRespDto resp = reactAgent.request(os, dto);
    }


}
