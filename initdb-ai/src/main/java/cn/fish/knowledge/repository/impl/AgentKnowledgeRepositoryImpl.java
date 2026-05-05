package cn.fish.knowledge.repository.impl;

import cn.fish.knowledge.dul.AgentKnowledgePo;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.info.KnowledgeInfo;
import cn.fish.knowledge.mapper.AgentKnowledgeMapper;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.hutool.core.util.ObjUtil;
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

    private AgentKnowledgePo convert(AgentKnowledge knowledge) {
        AgentKnowledgePo po = new AgentKnowledgePo();
        po.setId(knowledge.getKnowledgeId())
            .setEmbeddingStatus(knowledge.getEmbeddingStatus())
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

    private AgentKnowledge assemble(AgentKnowledgePo po) {
        if (ObjUtil.isNull(po)) {
            return null;
        }
        AgentKnowledge knowledge = new AgentKnowledge();
        knowledge.setKnowledgeId(po.getId())
            .setEmbeddingStatus(po.getEmbeddingStatus())
            .setKnowledgeInfo(new KnowledgeInfo()
                .setDatasourceId(po.getDatasourceId())
                .setTitle(po.getTitle())
                .setType(po.getType())
                .setQuestion(po.getQuestion())
                .setContent(po.getContent())
                .setIsRecall(po.getIsRecall())
                .setEmbeddingStatus(po.getEmbeddingStatus())
                .setErrorMsg(po.getErrorMsg())
                .setFileId(po.getFileId())
                .setFileSize(po.getFileSize())
                .setFileType(po.getFileType())
                .setSplitterType(po.getSplitterType())
            );
        return knowledge;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateKnowledge(AgentKnowledge knowledge) {
        AgentKnowledgePo po = convert(knowledge);
        updateById(po);
    }
}
