package cn.fish.initDB.event;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class ChartAutoSummarizeEvent extends ApplicationEvent {

    private final RunnableConfig config;

    public ChartAutoSummarizeEvent(Object source, RunnableConfig config) {
        super(source);
        this.config = config;
    }
}
