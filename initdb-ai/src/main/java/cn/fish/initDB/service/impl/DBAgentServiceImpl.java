package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.event.ChartAutoSummarizeEvent;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.entity.QuestionContextualizeResult;
import cn.fish.initDB.service.QuestionContextualizeService;
import cn.fish.initDB.workflow.DBAgentStateGraphConfig;
import cn.fish.initDB.chat.DbChatInputKeys;
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
            @Qualifier(DBAgentStateGraphConfig.DB_CHAT_WORKFLOW_BEAN) CompiledGraph dbChatWorkflow,
            ApplicationEventPublisher eventPublisher,
            QuestionContextualizeService questionContextualizeService) {
        this.dbChatWorkflow = dbChatWorkflow;
        this.eventPublisher = eventPublisher;
        this.questionContextualizeService = questionContextualizeService;
    }

    private static String streamLine(String part, String text) throws JsonProcessingException {
        Map<String, String> m = new LinkedHashMap<>(2);
        m.put("p", part);
        m.put("t", text == null ? "" : text);
        return STREAM_JSON.writeValueAsString(m) + "\n";
    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            return Flux.just(streamLineSafe("answer", "Sorry, an error occurred: sessionId is null"));
        }
        try {
            RunnableConfig config = RunnableConfig.builder()
                                                  .threadId(sessionId)
                                                  .mergeReasoningContent(true)
                                                  .build();

            QuestionContextualizeResult contextualize =
                    questionContextualizeService.rewrite(chatRequest.getMessage(), sessionId);
            Flux<String> contextualizeHeader = StringUtils.hasText(contextualize.getDisplay())
                    ? Flux.just(streamLineSafe("contextualize", contextualize.getDisplay()))
                    : Flux.empty();

            Map<String, Object> inputs = new LinkedHashMap<>(2);
            inputs.put(DbChatInputKeys.STANDALONE, contextualize.getStandalone());

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
                        // message().getText() 常为「截至当前帧的累积全文」；前端对 answer 做 += 会整段重复。
                        // 与 NodeOutputUtil 一致：优先使用 chunk() 作为增量片段。
                        String delta = streamingOutput.chunk();
                        if (!StringUtils.hasText(delta)) {
                            return Flux.empty();
                        }
                        return Flux.just(streamLineSafe("answer", delta));
                    });

            Flux<String> combined = Flux.concat(contextualizeHeader, answerFlux)
                                        .switchIfEmpty(Flux.defer(() -> {
                                            log.warn(
                                                    "chatStream emitted no text for sessionId={}, model may have returned empty tokens",
                                                    sessionId);
                                            return Flux.just(streamLineSafe("answer",
                                                    "未收到模型的文本输出，请重试。若多次出现，请检查模型 API、配额及网络；"
                                                            + "若刚做过对话压缩，可新建会话再试。"));
                                        }))
                                        .onErrorResume(e -> {
                                            if (e instanceof IllegalStateException && e.getMessage() != null
                                                    && e.getMessage().contains("Empty flux detected")) {
                                                log.warn("LLM stream empty for sessionId={}: {}", sessionId, e.getMessage());
                                                return Flux.just(streamLineSafe("answer",
                                                        "模型流式通道无有效内容（API 无结果、内容审核或上下文异常等）。请稍后重试，或新建会话。"
                                                                + " 详情：" + e.getMessage()));
                                            }
                                            log.error("chatStream failed sessionId={}", sessionId, e);
                                            return Flux.just(streamLineSafe("answer", "对话生成失败: " + e.getMessage()));
                                        });
            return combined;
        } catch (Exception e) {
            log.error("chatStream setup failed", e);
            return Flux.just(streamLineSafe("answer", "对话启动失败: " + e.getMessage()));
        }
    }

    private static String streamLineSafe(String part, String text) {
        try {
            return streamLine(part, text);
        } catch (JsonProcessingException e) {
            return "{\"p\":\"answer\",\"t\":\"流封装失败\"}\n";
        }
    }
}
