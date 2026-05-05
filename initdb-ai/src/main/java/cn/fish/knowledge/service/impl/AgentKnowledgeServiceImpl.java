package cn.fish.knowledge.service.impl;

import cn.fish.initDB.event.AgentKnowledgeAddEvent;
import cn.fish.knowledge.business.KnowledgeBo;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.fish.knowledge.service.AgentKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

@Slf4j
@RequiredArgsConstructor
public class AgentKnowledgeServiceImpl implements AgentKnowledgeService {
    private final AgentKnowledgeRepository agentKnowledgeRepository;

    private final ApplicationEventPublisher eventPublisher;



    @Override
    public void add(KnowledgeBo bo) {
        bo.standard();
        AgentKnowledge knowledge = bo.assemble();
        knowledge.init();
        agentKnowledgeRepository.saveKnowledge(knowledge);
        eventPublisher.publishEvent(new AgentKnowledgeAddEvent(this, knowledge.getKnowledgeId()));
    }

}
