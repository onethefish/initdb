package cn.fish.knowledge.repository;

import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.IRepository;

public interface AgentKnowledgeRepository extends IRepository<AgentKnowledge> {


    Page<AgentKnowledge> queryPage(AgentKnowledgeVO vo, Page<AgentKnowledge> page);

}
