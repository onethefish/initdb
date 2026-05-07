package cn.fish.knowledge.event;

import cn.fish.knowledge.entity.AgentKnowledge;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AgentKnowledgeDeleteEvent extends ApplicationEvent {

    private final AgentKnowledge agentKnowledge;

    public AgentKnowledgeDeleteEvent(Object source, AgentKnowledge agentKnowledge) {
        super(source);
        this.agentKnowledge = agentKnowledge;
    }
}
