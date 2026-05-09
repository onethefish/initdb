package cn.fish.initDB.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

@Slf4j
public class NodeOutputUtil {

    /**
     * 从流式 {@link StreamingOutput} 解析应展示给用户的文本增量。
     * 文本可能来自 {@link StreamingOutput#chunk()}、{@code originData}（常为 Spring AI {@link ChatResponse}）或 {@link AssistantMessage}。
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

    /**
     * 流式帧里 {@code originData} 常为 {@link ChatResponse}，不能用 {@code toString()} 当作回答文本。
     */
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

    /**
     * 仅输出助手可见正文；仅有 tool_calls、无用户可见文本时不输出（避免把 ToolCall 序列化进界面）。
     */
    private static String assistantVisibleText(AssistantMessage am) {
        if (am == null) {
            return "";
        }
        if (am.hasToolCalls() && StrUtil.isBlank(am.getText())) {
            return "";
        }
        return am.getText() != null ? am.getText() : "";
    }
}
