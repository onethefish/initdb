package cn.fish.knowledge.event;

import cn.fish.knowledge.entity.AgentKnowledge;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AgentKnowledgeEmbeddingEvent extends ApplicationEvent {

    private final AgentKnowledge agentKnowledge;

    public AgentKnowledgeEmbeddingEvent(Object source, AgentKnowledge agentKnowledge) {
        super(source);
        this.agentKnowledge = agentKnowledge;
    }
}
