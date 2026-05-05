package cn.fish.knowledge.repository;

import cn.fish.knowledge.dul.AgentKnowledgePo;
import cn.fish.knowledge.entity.AgentKnowledge;
import com.baomidou.mybatisplus.extension.repository.IRepository;

public interface AgentKnowledgeRepository extends IRepository<AgentKnowledgePo> {
    /**
     * 保存知识
     * @param knowledge knowledge
     */
    void saveKnowledge(AgentKnowledge knowledge);

    /**
     * 更新知识
     * @param knowledge knowledge
     */
    void updateKnowledge(AgentKnowledge knowledge);
}
