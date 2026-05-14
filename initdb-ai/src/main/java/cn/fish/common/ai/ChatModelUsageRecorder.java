package cn.fish.common.ai;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 记录单次 {@link org.springframework.ai.chat.model.ChatModel#call} 的 token usage（仅日志，不注册 Micrometer，避免进入 /actuator/prometheus 等导出端点）。
 */
@Component
public class ChatModelUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(ChatModelUsageRecorder.class);

    private static final String NA = "na";

    /**
     * @param operation     稳定英文标识，便于日志检索（如 {@code contextualize}）
     * @param response      非空
     * @param durationNanos {@link System#nanoTime()} 差值
     * @param correlationId 可选：sessionId、threadId 等
     */
    public void record(String operation, ChatResponse response, long durationNanos, @Nullable String correlationId) {
        if (ObjectUtil.isNull(response)) {
            return;
        }
        ChatResponseMetadata meta = response.getMetadata();
        Usage usage = ObjectUtil.isNotNull(meta) ? meta.getUsage() : null;
        Integer promptTokens = ObjectUtil.isNotNull(usage) ? usage.getPromptTokens() : null;
        Integer completionTokens = ObjectUtil.isNotNull(usage) ? usage.getCompletionTokens() : null;
        Integer totalTokens = ObjectUtil.isNotNull(usage) ? usage.getTotalTokens() : null;
        String model = ObjectUtil.isNotNull(meta) ? meta.getModel() : null;
        long durationMs = TimeUnit.NANOSECONDS.toMillis(durationNanos);
        String opTag = StrUtil.blankToDefault(operation, "unknown");

        if (log.isInfoEnabled()) {
            log.info(
                    "llm.usage operation={} promptTokens={} completionTokens={} totalTokens={} model={} durationMs={} correlationId={}",
                    opTag,
                    Convert.toStr(promptTokens, NA),
                    Convert.toStr(completionTokens, NA),
                    Convert.toStr(totalTokens, NA),
                    StrUtil.blankToDefault(model, NA),
                    durationMs,
                    StrUtil.blankToDefault(correlationId, NA));
        }
    }
}
