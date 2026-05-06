package cn.fish.knowledge.service.impl;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.knowledge.converter.AgentKnowledgeConverter;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.entity.AgentKnowledgeDTO;
import cn.fish.knowledge.event.AgentKnowledgeEmbeddingEvent;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.fish.knowledge.service.AgentKnowledgeService;
import cn.hutool.core.util.ObjectUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class AgentKnowledgeServiceImpl implements AgentKnowledgeService {

    private final AgentKnowledgeRepository agentKnowledgeRepository;

    private final ServaFile servaFile;


    private final ApplicationEventPublisher eventPublisher;

    public AgentKnowledgeServiceImpl(AgentKnowledgeRepository agentKnowledgeRepository, ApplicationEventPublisher eventPublisher,
                                     ServaFile servaFile) {
        this.agentKnowledgeRepository = agentKnowledgeRepository;
        this.eventPublisher = eventPublisher;
        this.servaFile = servaFile;
    }

    @Override
    @Transactional
    public void add(AgentKnowledgeDTO agentKnowledgeDTO) {
        String fileId = null;
        MultipartFile file = agentKnowledgeDTO.getFile();
        if (ObjectUtil.isNotEmpty(file)) {
            try {
                fileId = servaFile.upload(file.getInputStream());
            } catch (IOException e) {
                throw new CommonException("文件上传失败", e);
            }
        }
        AgentKnowledge knowledge = AgentKnowledgeConverter.toDataBaseEntity(agentKnowledgeDTO, fileId);
        agentKnowledgeRepository.save(knowledge);
        eventPublisher.publishEvent(new AgentKnowledgeEmbeddingEvent(this, knowledge));
    }
}
