package cn.fish.knowledge.service.impl;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.knowledge.converter.AgentKnowledgeConverter;
import cn.fish.common.convert.EntityConver;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.entity.AgentKnowledgeDTO;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import cn.fish.knowledge.event.AgentKnowledgeDeleteEvent;
import cn.fish.knowledge.event.AgentKnowledgeEmbeddingEvent;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.fish.knowledge.service.AgentKnowledgeService;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

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
    public IPage<AgentKnowledgeVO> queryPage(AgentKnowledgeVO vo, Page<AgentKnowledge> agentKnowledgePage) {
        Page<AgentKnowledge> result = agentKnowledgeRepository.queryPage(vo, agentKnowledgePage);
        return EntityConver.convertPage(result, AgentKnowledgeVO.class);
    }

    @Override
    public AgentKnowledgeVO queryUnique(AgentKnowledgeVO vo) {
        AgentKnowledge byId = agentKnowledgeRepository.getById(vo.getId());
        return EntityConver.convertObject(byId, AgentKnowledgeVO.class);
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

    @Override
    @Transactional
    public void update(AgentKnowledgeDTO agentKnowledgeDTO) {
        AgentKnowledge current = agentKnowledgeRepository.getById(agentKnowledgeDTO.getId());
        if (ObjectUtil.isEmpty(current)) {
            throw new CommonException("没有找到要修改的知识文档：" + agentKnowledgeDTO.getId());
        }
        current.setTitle(agentKnowledgeDTO.getTitle());
        current.setContent(agentKnowledgeDTO.getContent());
        agentKnowledgeRepository.updateById(current);
        eventPublisher.publishEvent(new AgentKnowledgeEmbeddingEvent(this, current));
    }

    @Override
    @Transactional
    public void delete(AgentKnowledgeDTO agentKnowledgeDTO) {
        AgentKnowledge knowledge = AgentKnowledgeConverter.toEmbeddingEntity(agentKnowledgeDTO);
        agentKnowledgeRepository.removeById(agentKnowledgeDTO.getId());
        eventPublisher.publishEvent(new AgentKnowledgeDeleteEvent(this, knowledge));
    }


    @Override
    @Transactional
    public void delete(List<AgentKnowledgeDTO> agentKnowledgeDTOList) {
        // 有业务逻辑 不能直接批量
        for (AgentKnowledgeDTO agentKnowledgeDTO : agentKnowledgeDTOList) {
            delete(agentKnowledgeDTO);
        }
    }

    @Override
    @Transactional
    public void refresh(List<AgentKnowledgeDTO> agentKnowledgeDTOList) {
        for (AgentKnowledgeDTO agentKnowledgeDTO : agentKnowledgeDTOList) {
            AgentKnowledge current = AgentKnowledgeConverter.toEmbeddingEntity(agentKnowledgeDTO);
            eventPublisher.publishEvent(new AgentKnowledgeEmbeddingEvent(this, current));
        }
    }
}
