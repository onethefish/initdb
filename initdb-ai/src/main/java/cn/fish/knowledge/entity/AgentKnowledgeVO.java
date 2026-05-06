package cn.fish.knowledge.entity;

import cn.fish.knowledge.enums.EmbeddingStatus;
import cn.fish.knowledge.enums.KnowledgeType;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentKnowledgeVO extends AgentKnowledge {

//    private String embeddingStatusValue;
//
//    private String typeValue;

    public String getEmbeddingStatusValue() {
        return EmbeddingStatus.getValueByCode(getEmbeddingStatus());
    }

    public String getTypeValue() {
        return KnowledgeType.getValueByCode(getType());
    }
}
