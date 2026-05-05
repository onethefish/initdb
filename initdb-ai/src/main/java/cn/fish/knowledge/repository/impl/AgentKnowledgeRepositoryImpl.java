package cn.fish.knowledge.repository.impl;

import cn.fish.knowledge.dul.AgentKnowledgePo;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.mapper.AgentKnowledgeMapper;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AgentKnowledgeRepositoryImpl extends CrudRepository<AgentKnowledgeMapper, AgentKnowledgePo> implements AgentKnowledgeRepository {
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveKnowledge(AgentKnowledge knowledge) {
        AgentKnowledgePo po = convert(knowledge);
        save(po);
        knowledge.setKnowledgeId(po.getId());
    }

    private static AgentKnowledgePo convert(AgentKnowledge knowledge) {
        AgentKnowledgePo po = new AgentKnowledgePo();
        po
            .setDatasourceId(knowledge.getKnowledgeInfo().getDatasourceId())
            .setTitle(knowledge.getKnowledgeInfo().getTitle())
            .setType(knowledge.getKnowledgeInfo().getType())
            .setQuestion(knowledge.getKnowledgeInfo().getQuestion())
            .setContent(knowledge.getKnowledgeInfo().getContent())
            .setIsRecall(knowledge.getKnowledgeInfo().getIsRecall())
            .setEmbeddingStatus(knowledge.getKnowledgeInfo().getEmbeddingStatus())
            .setErrorMsg(knowledge.getKnowledgeInfo().getErrorMsg())
            .setFileId(knowledge.getKnowledgeInfo().getFileId())
            .setFileSize(knowledge.getKnowledgeInfo().getFileSize())
            .setFileType(knowledge.getKnowledgeInfo().getFileType())
            .setSplitterType(knowledge.getKnowledgeInfo().getSplitterType())
        ;
        return po;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(AgentKnowledge knowledge) {
        AgentKnowledgePo po = convert(knowledge);
        updateById(po);
    }
}
