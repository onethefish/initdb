package cn.fish.initDB.event;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Collection;
import java.util.Map;

@Slf4j
@Component
public class ChartEventListener {


    private final BaseCheckpointSaver baseCheckpointSaver;
    private final ReactAgent summaryAgent;

    private static final String SUMMARY_PROMPT = """
            你是对话总结助手。将以下对话历史总结为简洁的要点:
            - 用户的核心需求
            - 涉及的关键表和字段
            - 重要的SQL查询或结论
            
            要求:
            - 用中文总结
            - 控制在100字以内
            - 保留关键技术细节
            """;

    public ChartEventListener(ChatModel chatModel, BaseCheckpointSaver baseCheckpointSaver) {
        this.baseCheckpointSaver = baseCheckpointSaver;
        this.summaryAgent = ReactAgent.builder()
                                      .name("对话总结助手")
                                      .systemPrompt(SUMMARY_PROMPT)
                                      .model(chatModel)
                                      .enableLogging(false)
                                      .build();
    }

    @Async("initDbExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleEmbeddingEvent(ChartAutoSummarizeEvent event) {
        RunnableConfig config = event.getConfig();
        try {
            Collection<Checkpoint> checkpoints = baseCheckpointSaver.list(config);
            if (checkpoints.size() >= 4) {
                StringBuilder conversationHistory = new StringBuilder();
                for (Checkpoint checkpoint : checkpoints) {
                    for (Map.Entry<String, Object> entry : checkpoint.getState().entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        if ("messages".equals(key) && value != null) {
                            conversationHistory.append(value.toString()).append("\n");
                        }
                    }
                }

                if (!conversationHistory.isEmpty()) {
                    summaryAgent.invoke(conversationHistory.toString(), config);
                    log.info("Auto summary generated for session: {}", config.threadId().orElse("unknown"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate auto summary", e);
        }

    }


}
