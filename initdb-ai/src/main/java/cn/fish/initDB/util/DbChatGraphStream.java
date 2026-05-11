package cn.fish.initDB.util;

import cn.fish.initDB.constants.InitDBConstants;
import cn.fish.initDB.workflow.DbWorkflowBundle;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * DB 聊天工作流 {@link com.alibaba.cloud.ai.graph.CompiledGraph#stream} 的应答侧处理：
 * {@link OutputType} 白名单、从 {@link StreamingOutput} 抽取用户可见文本、NDJSON 行封装（与 {@code static/js/chat.js} 协议一致）。
 * 工作流阶段说明使用 {@link InitDBConstants#STREAM_PART_TRACE}，与正文 {@link InitDBConstants#STREAM_PART_ANSWER} 分离。
 */
public final class DbChatGraphStream {

    /** ReAct 子图内模型流式帧的 {@link NodeOutput#node()} 占位名，与外层 {@link InitDBConstants#NODE_DB_REACT} 不同 */
    private static final String GRAPH_INTERNAL_AGENT_MODEL_NODE = "_AGENT_MODEL_";

    /** 思考过程里展示的当前问句摘要长度上限（避免 NDJSON 过大） */
    private static final int TRACE_STANDALONE_SNIPPET_MAX_CHARS = 120;

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

    /**
     * 将单帧 {@link NodeOutput} 转为发往客户端的 NDJSON 流：结构 trace →（流式帧时）工具/段 trace →正文 answer。
     * 各 {@link AtomicReference} 须在整条 {@code chatStream} 订阅内复用，用于去重。
     */
    public static Flux<String> concatChatStreamNdjsonLines(
            NodeOutput nodeOutput,
            AtomicReference<String> structuralTraceKey,
            AtomicReference<String> toolTraceSignature,
            AtomicReference<String> streamTraceSegmentKey) {
        return Flux.concat(
                fluxStructuralTraceNdjson(nodeOutput, structuralTraceKey),
                fluxStreamingTraceExtras(nodeOutput, toolTraceSignature, streamTraceSegmentKey),
                mapNodeToAnswerNdjsonLines(nodeOutput));
    }

    /** 工作流同步阶段（意图 / 桥接 / NL2SQL / 校验等）→ {@code trace} */
    private static Flux<String> fluxStructuralTraceNdjson(NodeOutput nodeOutput, AtomicReference<String> structuralTraceKey) {
        return optionalLineToFlux(tryEmitStructuralTraceNdjsonLine(nodeOutput, structuralTraceKey));
    }

    /** 流式帧：工具说明 → 流式段说明，均为 {@code trace}；非 {@link StreamingOutput} 时为空 */
    private static Flux<String> fluxStreamingTraceExtras(
            NodeOutput nodeOutput,
            AtomicReference<String> toolTraceSignature,
            AtomicReference<String> streamTraceSegmentKey) {
        if (!(nodeOutput instanceof StreamingOutput<?> so)) {
            return Flux.empty();
        }
        return Flux.concat(
                optionalLineToFlux(tryEmitToolTraceNdjsonLine(so, toolTraceSignature)),
                optionalLineToFlux(tryEmitTraceNdjsonLine(nodeOutput, streamTraceSegmentKey)));
    }

    private static Flux<String> optionalLineToFlux(Optional<String> oneNdjsonLine) {
        return oneNdjsonLine.map(Flux::just).orElseGet(Flux::empty);
    }

    /**
     * 在同一流式通道上，当 {@link StreamingOutput} 的节点或输出类型切换时，发出一行 {@code trace} NDJSON（供前端「思考过程」区）。
     *
     * @param lastSegmentKey 上一段已上报的键，由调用方在同一次 chatStream 订阅内复用
     */
    public static Optional<String> tryEmitTraceNdjsonLine(NodeOutput nodeOutput, AtomicReference<String> lastSegmentKey) {
        if (!(nodeOutput instanceof StreamingOutput<?> streamingOutput)) {
            return Optional.empty();
        }
        if (!allowsStreamingOutput(streamingOutput)) {
            return Optional.empty();
        }
        String key = streamSegmentKey(streamingOutput);
        if (key == null) {
            return Optional.empty();
        }
        String prev = lastSegmentKey.get();
        if (Objects.equals(prev, key)) {
            return Optional.empty();
        }
        lastSegmentKey.set(key);
        String line = describeStreamSegment(streamingOutput);
        return Optional.of(streamLineSafe(InitDBConstants.STREAM_PART_TRACE, line));
    }

    /**
     * 图内同步节点完成等非流式 {@link NodeOutput} 的阶段性说明（依赖框架是否在 stream 中推送此类帧）。
     */
    public static Optional<String> tryEmitStructuralTraceNdjsonLine(NodeOutput nodeOutput, AtomicReference<String> lastStructuralKey) {
        if (nodeOutput instanceof StreamingOutput<?>) {
            return Optional.empty();
        }
        String node = nodeOutput.node();
        if (!StringUtils.hasText(node)) {
            return Optional.empty();
        }
        OverAllState st = nodeOutput.state();
        Map<String, Object> bundle = DbWorkflowBundle.readCopy(st);
        Optional<String> lineOpt = buildStructuralTraceLine(node, bundle);
        if (lineOpt.isEmpty()) {
            return Optional.empty();
        }
        String dedupeKey = "struct:" + node + ":" + structuralPayloadKey(node, bundle);
        String prev = lastStructuralKey.get();
        if (Objects.equals(prev, dedupeKey)) {
            return Optional.empty();
        }
        lastStructuralKey.set(dedupeKey);
        return lineOpt.map(l -> streamLineSafe(InitDBConstants.STREAM_PART_TRACE, l));
    }

    /**
     * 模型流式帧上出现 {@link AssistantMessage#getToolCalls()} 时追加说明（与「模型输出正文」区分）。
     */
    public static Optional<String> tryEmitToolTraceNdjsonLine(StreamingOutput<?> streamingOutput, AtomicReference<String> lastToolSignature) {
        if (!allowsStreamingOutput(streamingOutput) || streamingOutput.getOutputType() != OutputType.AGENT_MODEL_STREAMING) {
            return Optional.empty();
        }
        var msg = streamingOutput.message();
        if (!(msg instanceof AssistantMessage am) || !am.hasToolCalls()) {
            return Optional.empty();
        }
        StringBuilder sig = new StringBuilder();
        for (ToolCall tc : am.getToolCalls()) {
            if (tc == null) {
                continue;
            }
            sig.append(tc.id()).append(':').append(tc.name()).append(';');
        }
        if (sig.length() == 0) {
            return Optional.empty();
        }
        String sigStr = sig.toString();
        if (Objects.equals(lastToolSignature.get(), sigStr)) {
            return Optional.empty();
        }
        lastToolSignature.set(sigStr);
        String names = am.getToolCalls().stream()
                           .filter(Objects::nonNull)
                           .map(ToolCall::name)
                           .filter(StringUtils::hasText)
                           .collect(Collectors.joining("，"));
        if (!StringUtils.hasText(names)) {
            names = "（未命名工具）";
        }
        if (names.length() > 200) {
            names = names.substring(0, 200) + "…";
        }
        String line = "【" + InitDBConstants.DB_REACT_AGENT_DISPLAY_NAME + "】调用工具：" + names;
        return Optional.of(streamLineSafe(InitDBConstants.STREAM_PART_TRACE, line));
    }

    /**
     * 去重键须在同一轮流式输出上保持稳定；切勿绑定 {@code message} 引用——框架常每帧新建实例，会导致 trace 每条 token 重复一条。
     */
    private static String streamSegmentKey(StreamingOutput<?> streamingOutput) {
        String node = streamingOutput.node();
        OutputType ot = streamingOutput.getOutputType();
        if (!StringUtils.hasText(node) && ot == null) {
            return null;
        }
        return (node != null ? node : "") + '\0' + (ot != null ? ot.name() : "");
    }

    private static String describeStreamSegment(StreamingOutput<?> streamingOutput) {
        String node = streamingOutput.node() != null ? streamingOutput.node() : "";
        OutputType ot = streamingOutput.getOutputType();
        OverAllState st = streamingOutput.state();
        String q = standaloneOneLineForTrace(st);
        if (InitDBConstants.NODE_DB_DIRECT_EXECUTE.equals(node) && ot == OutputType.GRAPH_NODE_STREAMING) {
            String head = "【直连执行】正在生成答复（含 SQL 与结果表格）";
            return appendQuestionSnippetLine(head, q);
        }
        if (ot == OutputType.AGENT_MODEL_STREAMING
                && (InitDBConstants.NODE_DB_REACT.equals(node) || GRAPH_INTERNAL_AGENT_MODEL_NODE.equals(node))) {
            String head = "【" + InitDBConstants.DB_REACT_AGENT_DISPLAY_NAME + "】正在组织自然语言回答";
            return appendQuestionSnippetLine(head, q);
        }
        if (ot == OutputType.AGENT_MODEL_STREAMING) {
            String head = "【" + traceNodeDisplayLabel(node) + "】模型输出中";
            return appendQuestionSnippetLine(head, q);
        }
        if (ot == OutputType.GRAPH_NODE_STREAMING) {
            String head = "【" + traceNodeDisplayLabel(node) + "】节点流式输出中";
            return appendQuestionSnippetLine(head, q);
        }
        return appendQuestionSnippetLine("【" + traceNodeDisplayLabel(node) + "】" + (ot != null ? ot.name() : "streaming"), q);
    }

    private static String appendQuestionSnippetLine(String head, String standaloneOneLine) {
        if (!StringUtils.hasText(standaloneOneLine)) {
            return head + "…";
        }
        return head + "；当前问句摘要：「" + standaloneOneLine + "」…";
    }

    private static String standaloneOneLineForTrace(OverAllState state) {
        if (state == null) {
            return "";
        }
        String raw = state.value(InitDBConstants.STANDALONE, "");
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String oneLine = raw.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        if (oneLine.length() <= TRACE_STANDALONE_SNIPPET_MAX_CHARS) {
            return oneLine;
        }
        return oneLine.substring(0, TRACE_STANDALONE_SNIPPET_MAX_CHARS) + "…";
    }

    private static Optional<String> buildStructuralTraceLine(String node, Map<String, Object> bundle) {
        if (InitDBConstants.NODE_DB_INTENT.equals(node)) {
            String route = DbWorkflowBundle.bundleString(bundle, InitDBConstants.STATE_KEY_DB_ROUTE, "");
            return Optional.of("【意图识别】问句已分析，本轮分支：" + routeTraceLabel(route));
        }
        if (InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE.equals(node)) {
            return Optional.of("【会话衔接】已进入「" + InitDBConstants.DB_REACT_AGENT_DISPLAY_NAME + "」推理链路（结合上下文与工具查数）…");
        }
        if (InitDBConstants.NODE_DB_DIRECT_TABLE_CATALOG.equals(node)) {
            return Optional.of("【直连数据】已加载当前库表清单（表名与注释），准备生成 SQL…");
        }
        if (InitDBConstants.NODE_DB_DIRECT_NL2SQL.equals(node)) {
            return Optional.of("【直连数据】正在根据问句生成可执行 SQL…");
        }
        if (InitDBConstants.NODE_DB_DIRECT_SQL_GUARD.equals(node)) {
            Object okObj = bundle.get(InitDBConstants.STATE_KEY_SQL_GUARD_OK);
            boolean ok = Boolean.TRUE.equals(okObj)
                    || (okObj instanceof String s && "true".equalsIgnoreCase(s.trim()));
            return Optional.of(ok
                    ? "【直连数据】SQL 已通过校验，准备执行查询。"
                    : "【直连数据】SQL 未通过校验，本次不执行查询。");
        }
        return Optional.empty();
    }

    private static String structuralPayloadKey(String node, Map<String, Object> bundle) {
        if (InitDBConstants.NODE_DB_INTENT.equals(node)) {
            return DbWorkflowBundle.bundleString(bundle, InitDBConstants.STATE_KEY_DB_ROUTE, "");
        }
        if (InitDBConstants.NODE_DB_AGENT_INPUT_BRIDGE.equals(node)) {
            return DbWorkflowBundle.bundleString(bundle, InitDBConstants.STATE_KEY_DB_ROUTE, "");
        }
        if (InitDBConstants.NODE_DB_DIRECT_TABLE_CATALOG.equals(node)) {
            return String.valueOf(DbWorkflowBundle.bundleString(bundle, InitDBConstants.STATE_KEY_DIRECT_TABLE_CATALOG_JSON, "").length());
        }
        if (InitDBConstants.NODE_DB_DIRECT_NL2SQL.equals(node)) {
            return String.valueOf(DbWorkflowBundle.bundleString(bundle, InitDBConstants.STATE_KEY_GENERATED_SQL, "").hashCode());
        }
        if (InitDBConstants.NODE_DB_DIRECT_SQL_GUARD.equals(node)) {
            return String.valueOf(bundle.get(InitDBConstants.STATE_KEY_SQL_GUARD_OK))
                    + "|" + DbWorkflowBundle.bundleString(bundle, InitDBConstants.STATE_KEY_GENERATED_SQL, "").length();
        }
        return "";
    }

    private static String routeTraceLabel(String route) {
        if (InitDBConstants.ROUTE_DIRECT_DATA_VALUE.equals(String.valueOf(route).trim())) {
            return "直连取数（表清单 → 生成 SQL → 校验 → 执行）";
        }
        return "对话分析（ReAct，智能体推理与工具）";
    }

    private static String traceNodeDisplayLabel(String node) {
        if (!StringUtils.hasText(node)) {
            return "工作流";
        }
        if (GRAPH_INTERNAL_AGENT_MODEL_NODE.equals(node)) {
            return InitDBConstants.DB_REACT_AGENT_DISPLAY_NAME;
        }
        return node;
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
