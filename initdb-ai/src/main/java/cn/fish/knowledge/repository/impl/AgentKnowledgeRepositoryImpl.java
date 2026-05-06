package cn.fish.knowledge.repository.impl;

import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.mapper.AgentKnowledgeMapper;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AgentKnowledgeRepositoryImpl extends CrudRepository<AgentKnowledgeMapper, AgentKnowledge> implements AgentKnowledgeRepository {

}
