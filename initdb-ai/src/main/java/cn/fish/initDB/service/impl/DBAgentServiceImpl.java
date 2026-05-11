package cn.fish.initDB.service.impl;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.event.ChartAutoSummarizeEvent;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.util.DbChatGraphStream;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

    private final CompiledGraph dbChatWorkflow;
    private final ApplicationEventPublisher eventPublisher;

    public DBAgentServiceImpl(
            @Qualifier(InitDBConstants.DB_CHAT_WORKFLOW_BEAN) CompiledGraph dbChatWorkflow,
            ApplicationEventPublisher eventPublisher) {
        this.dbChatWorkflow = dbChatWorkflow;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            return Flux.just(DbChatGraphStream.streamLineSafe(InitDBConstants.STREAM_PART_ANSWER, InitDBConstants.CHAT_STREAM_ERR_SESSION_NULL));
        }
        /*
         * 会话问句补全不在本接口执行（见 /db/chat/contextualize）；图内 NodeAction（含 chatModel.call）为阻塞调用。
         * WebFlux 若在事件循环线程上订阅，会卡住其它 I/O，表现为「多段日志交错 / 请求挂死」。整段迁到 boundedElastic。
         */
        return Flux.defer(() -> {
            try {
                RunnableConfig config = RunnableConfig.builder()
                                                      .threadId(sessionId)
                                                      .mergeReasoningContent(true)
                                                      .build();

                String effectiveStandalone = resolveStandalone(chatRequest);

                Map<String, Object> inputs = new LinkedHashMap<>(8);
                inputs.put(InitDBConstants.STANDALONE, effectiveStandalone);
                inputs.put(InitDBConstants.STATE_KEY_DB_BUNDLE, DbWorkflowBundle.newInitialBundle(sessionId));

                Flux<NodeOutput> stream = dbChatWorkflow.stream(inputs, config);
                AtomicReference<String> streamTraceSegmentKey = new AtomicReference<>(null);
                AtomicReference<String> structuralTraceKey = new AtomicReference<>(null);
                AtomicReference<String> toolTraceSignature = new AtomicReference<>(null);
                Flux<String> answerFlux = stream.doOnComplete(() -> eventPublisher.publishEvent(new ChartAutoSummarizeEvent(this, config)))
                                                // 排除 START/END：END 常带整段合并 state，与直连 Flux 的 GRAPH_NODE_STREAMING 片段叠加会让前端 answer 重复累积。
                                                .filter(nodeOutput -> !nodeOutput.isSTART() && !nodeOutput.isEND())
                                                .concatMap(nodeOutput -> DbChatGraphStream.concatChatStreamNdjsonLines(
                                                        nodeOutput, structuralTraceKey, toolTraceSignature, streamTraceSegmentKey));
                return answerFlux
                           .switchIfEmpty(Flux.defer(() -> {
                               log.warn("chatStream emitted no text for sessionId={}, model may have returned empty tokens", sessionId);
                               return Flux.just(DbChatGraphStream.streamLineSafe(InitDBConstants.STREAM_PART_ANSWER, InitDBConstants.CHAT_STREAM_ERR_EMPTY_MODEL));
                           }))
                           .onErrorResume(e -> createErrorFlux(e, sessionId));
            } catch (Exception e) {
                return createErrorFlux(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private static String resolveStandalone(ChatRequest chatRequest) {
        String fromClient = StrUtil.trimToNull(chatRequest.getStandalone());
        if (fromClient != null) {
            return clampStandaloneBody(fromClient);
        }
        String fromMessage = StrUtil.trimToNull(chatRequest.getMessage());
        return fromMessage != null ? clampStandaloneBody(fromMessage) : "";
    }

    private static String clampStandaloneBody(String s) {
        if (StrUtil.isEmpty(s)) {
            return "";
        }
        String t = s.trim();
        int max = InitDBConstants.CONTEXTUALIZE_BODY_MAX_CHARS;
        if (t.length() > max) {
            return t.substring(0, max);
        }
        return t;
    }

    private static Flux<String> createErrorFlux(Throwable e) {
        log.error("chatStream setup failed", e);
        return Flux.just(DbChatGraphStream.streamLineSafe(InitDBConstants.STREAM_PART_ANSWER, InitDBConstants.CHAT_STREAM_ERR_SETUP_PREFIX + e.getMessage()));
    }

    private static Flux<String> createErrorFlux(Throwable e, String sessionId) {
        if (e instanceof IllegalStateException && e.getMessage() != null && e.getMessage().contains("Empty flux detected")) {
            log.warn("LLM stream empty for sessionId={}: {}", sessionId, e.getMessage());
            return Flux.just(DbChatGraphStream.streamLineSafe(InitDBConstants.STREAM_PART_ANSWER,
                    InitDBConstants.CHAT_STREAM_ERR_EMPTY_FLUX_PREFIX + e.getMessage()));
        }
        log.error("chatStream failed sessionId={}", sessionId, e);
        return Flux.just(DbChatGraphStream.streamLineSafe(InitDBConstants.STREAM_PART_ANSWER, InitDBConstants.CHAT_STREAM_ERR_FAILED_PREFIX + e.getMessage()));
    }

}
