package cn.fish.initDB.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AgentKnowledgeAddEvent extends ApplicationEvent {

    private final String knowledgeId;

    public AgentKnowledgeAddEvent(Object source, String knowledgeId) {
        super(source);
        this.knowledgeId = knowledgeId;
    }
}
