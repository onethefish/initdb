package cn.fish.knowledge.service;

import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.entity.AgentKnowledgeDTO;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface AgentKnowledgeService {

    IPage<AgentKnowledgeVO> queryPage(AgentKnowledgeVO vo, Page<AgentKnowledge> agentKnowledgePage);

    AgentKnowledgeVO queryUnique(AgentKnowledgeVO vo);

    void add(AgentKnowledgeDTO agentKnowledgeDTO);

    void update(AgentKnowledgeDTO agentKnowledgeDTO);

    void delete(AgentKnowledgeDTO agentKnowledgeDTO);

    void delete(List<AgentKnowledgeDTO> agentKnowledgeDTOList);

    void refresh(List<AgentKnowledgeDTO> agentKnowledgeDTOList);
}
