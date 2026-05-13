package cn.fish.initDB.event;

import org.springframework.context.ApplicationEvent;

/**
 * 新导出任务已持久化且事务提交后发布，用于唤醒调度立即处理队列，减少空闲轮询。
 */
public class ExportJobPendingEvent extends ApplicationEvent {

    public ExportJobPendingEvent(Object source) {
        super(source);
    }
}
