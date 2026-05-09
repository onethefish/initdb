package cn.fish.initDB.util;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * DB 聊天工作流 {@link com.alibaba.cloud.ai.graph.CompiledGraph#stream} 的应答侧处理：
 * {@link OutputType} 白名单、从 {@link StreamingOutput} 抽取用户可见文本、NDJSON 行封装（与 {@code static/js/chat.js} 协议一致）。
 */
public final class DbChatGraphStream {

    private static final ObjectMapper STREAM_JSON = new ObjectMapper();

    private static final Set<OutputType> STREAM_TEXT_OUTPUT_TYPES = EnumSet.of(
            OutputType.AGENT_MODEL_STREAMING,
            OutputType.GRAPH_NODE_STREAMING
    );

    private DbChatGraphStream() {
    }

    /**
     * 单帧 → 零或多行 NDJSON，{@code p} 固定为 {@link InitDBConstants#STREAM_PART_ANSWER}。
     */
    public static Flux<String> mapNodeToAnswerNdjsonLines(NodeOutput nodeOutput) {
        return mapNodeToAnswerDeltas(nodeOutput)
                .map(delta -> streamLineSafe(InitDBConstants.STREAM_PART_ANSWER, delta));
    }

    private static Flux<String> mapNodeToAnswerDeltas(NodeOutput nodeOutput) {
        if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
            if (!allowsStreamingOutput(streamingOutput)) {
                return Flux.empty();
            }
            String delta = streamingTextDelta(streamingOutput);
            if (!StringUtils.hasText(delta)) {
                return Flux.empty();
            }
            return Flux.just(delta);
        }
        return DbWorkflowBundle.directAnswerFrom(nodeOutput)
                               .map(DbChatGraphStream::coerceToTrimmedString)
                               .filter(StringUtils::hasText)
                               .map(Flux::just)
                               .orElseGet(Flux::empty);
    }

    private static boolean allowsStreamingOutput(StreamingOutput<?> streamingOutput) {
        OutputType ot = streamingOutput.getOutputType();
        return ot != null && STREAM_TEXT_OUTPUT_TYPES.contains(ot);
    }

    private static String coerceToTrimmedString(Object v) {
        if (v == null) {
            return "";
        }
        return String.valueOf(v).trim();
    }

    /**
     * 从流式 {@link StreamingOutput} 解析应展示给用户的文本增量。
     */
    public static String streamingTextDelta(StreamingOutput<?> streamingOutput) {
        String c = streamingOutput.chunk();
        if (StrUtil.isNotBlank(c)) {
            return c;
        }
        Object origin = streamingOutput.getOriginData();
        String fromOrigin = originDataToVisibleText(origin);
        if (StrUtil.isNotBlank(fromOrigin)) {
            return fromOrigin;
        }
        var msg = streamingOutput.message();
        if (msg instanceof AssistantMessage am) {
            return assistantVisibleText(am);
        }
        if (msg != null) {
            return msg.getText() != null ? msg.getText() : "";
        }
        return "";
    }

    private static String originDataToVisibleText(Object origin) {
        if (origin == null) {
            return "";
        }
        if (origin instanceof ChatResponse cr) {
            return springAiChatResponseToAssistantText(cr);
        }
        if (origin instanceof AssistantMessage am) {
            return assistantVisibleText(am);
        }
        if (origin.getClass().getName().startsWith("org.springframework.ai.")) {
            return "";
        }
        return String.valueOf(origin);
    }

    private static String springAiChatResponseToAssistantText(ChatResponse cr) {
        if (cr.getResults().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object item : cr.getResults()) {
            if (!(item instanceof Generation gen)) {
                continue;
            }
            AssistantMessage am = gen.getOutput();
            String piece = assistantVisibleText(am);
            if (StrUtil.isNotBlank(piece)) {
                sb.append(piece);
            }
        }
        return sb.toString();
    }

    private static String assistantVisibleText(AssistantMessage am) {
        if (am == null) {
            return "";
        }
        if (am.hasToolCalls() && StrUtil.isBlank(am.getText())) {
            return "";
        }
        return am.getText() != null ? am.getText() : "";
    }

    public static String streamLineSafe(String part, String text) {
        try {
            return streamLine(part, text);
        } catch (JsonProcessingException e) {
            return InitDBConstants.CHAT_STREAM_JSON_SERIALIZE_FAILED;
        }
    }

    private static String streamLine(String part, String text) throws JsonProcessingException {
        Map<String, String> m = new LinkedHashMap<>(2);
        m.put(InitDBConstants.NDJSON_KEY_PART, part);
        m.put(InitDBConstants.NDJSON_KEY_TEXT, text == null ? "" : text);
        return STREAM_JSON.writeValueAsString(m) + "\n";
    }
}
