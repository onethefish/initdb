package cn.fish.initDB.service.impl;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.QuestionContextualizeResult;
import cn.fish.initDB.event.ChartAutoSummarizeEvent;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.service.QuestionContextualizeService;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

    private static final ObjectMapper STREAM_JSON = new ObjectMapper();

    private final CompiledGraph dbChatWorkflow;
    private final ApplicationEventPublisher eventPublisher;
    private final QuestionContextualizeService questionContextualizeService;

    public DBAgentServiceImpl(
            @Qualifier(InitDBConstants.DB_CHAT_WORKFLOW_BEAN) CompiledGraph dbChatWorkflow,
            ApplicationEventPublisher eventPublisher,
            QuestionContextualizeService questionContextualizeService) {
        this.dbChatWorkflow = dbChatWorkflow;
        this.eventPublisher = eventPublisher;
        this.questionContextualizeService = questionContextualizeService;
    }

    private static String streamLine(String part, String text) throws JsonProcessingException {
        Map<String, String> m = new LinkedHashMap<>(2);
        m.put(InitDBConstants.NDJSON_KEY_PART, part);
        m.put(InitDBConstants.NDJSON_KEY_TEXT, text == null ? "" : text);
        return STREAM_JSON.writeValueAsString(m) + "\n";
    }

    @SuppressWarnings("deprecation")
    private static String agentModelStreamingChunk(StreamingOutput<?> streamingOutput) {
        return streamingOutput.chunk();
    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            return Flux.just(streamLineSafe(InitDBConstants.STREAM_PART_ANSWER, InitDBConstants.CHAT_STREAM_ERR_SESSION_NULL));
        }
        try {
            RunnableConfig config = RunnableConfig.builder()
                                                  .threadId(sessionId)
                                                  .mergeReasoningContent(true)
                                                  .build();

            QuestionContextualizeResult contextualize =
                    questionContextualizeService.rewrite(chatRequest.getMessage(), sessionId);
            Flux<String> contextualizeHeader = StringUtils.hasText(contextualize.getDisplay())
                    ? Flux.just(streamLineSafe(InitDBConstants.STREAM_PART_CONTEXTUALIZE, contextualize.getDisplay()))
                    : Flux.empty();

            Map<String, Object> inputs = new LinkedHashMap<>(2);
            inputs.put(InitDBConstants.STANDALONE, contextualize.getStandalone());

            Flux<NodeOutput> stream = dbChatWorkflow.stream(inputs, config);
            Flux<String> answerFlux = stream
                    .doOnComplete(() -> eventPublisher.publishEvent(new ChartAutoSummarizeEvent(this, config)))
                    .filter(nodeOutput -> !nodeOutput.isSTART() && !nodeOutput.isEND())
                    .concatMap(nodeOutput -> {
                        if (!(nodeOutput instanceof StreamingOutput<?> streamingOutput)) {
                            return Flux.empty();
                        }
                        if (!Objects.equals(OutputType.AGENT_MODEL_STREAMING, streamingOutput.getOutputType())) {
                            return Flux.empty();
                        }
                        String delta = agentModelStreamingChunk(streamingOutput);
                        if (!StringUtils.hasText(delta)) {
                            return Flux.empty();
                        }
                        return Flux.just(streamLineSafe(InitDBConstants.STREAM_PART_ANSWER, delta));
                    });

            Flux<String> combined = Flux.concat(contextualizeHeader, answerFlux)
                                        .switchIfEmpty(Flux.defer(() -> {
                                            log.warn(
                                                    "chatStream emitted no text for sessionId={}, model may have returned empty tokens",
                                                    sessionId);
                                            return Flux.just(streamLineSafe(
                                                    InitDBConstants.STREAM_PART_ANSWER,
                                                    InitDBConstants.CHAT_STREAM_ERR_EMPTY_MODEL));
                                        }))
                                        .onErrorResume(e -> {
                                            if (e instanceof IllegalStateException && e.getMessage() != null
                                                    && e.getMessage().contains("Empty flux detected")) {
                                                log.warn("LLM stream empty for sessionId={}: {}", sessionId, e.getMessage());
                                                return Flux.just(streamLineSafe(
                                                        InitDBConstants.STREAM_PART_ANSWER,
                                                        InitDBConstants.CHAT_STREAM_ERR_EMPTY_FLUX_PREFIX
                                                                + e.getMessage()));
                                            }
                                            log.error("chatStream failed sessionId={}", sessionId, e);
                                            return Flux.just(streamLineSafe(
                                                    InitDBConstants.STREAM_PART_ANSWER,
                                                    InitDBConstants.CHAT_STREAM_ERR_FAILED_PREFIX + e.getMessage()));
                                        });
            return combined;
        } catch (Exception e) {
            log.error("chatStream setup failed", e);
            return Flux.just(streamLineSafe(
                    InitDBConstants.STREAM_PART_ANSWER,
                    InitDBConstants.CHAT_STREAM_ERR_SETUP_PREFIX + e.getMessage()));
        }
    }

    private static String streamLineSafe(String part, String text) {
        try {
            return streamLine(part, text);
        } catch (JsonProcessingException e) {
            return InitDBConstants.CHAT_STREAM_JSON_SERIALIZE_FAILED;
        }
    }
}
