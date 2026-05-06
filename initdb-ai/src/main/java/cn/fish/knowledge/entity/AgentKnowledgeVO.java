package cn.fish.knowledge.entity;

import cn.fish.knowledge.enums.EmbeddingStatus;
import cn.fish.knowledge.enums.KnowledgeType;
import cn.fish.knowledge.enums.SplitterType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentKnowledgeVO extends AgentKnowledge {

    //    private String embeddingStatusValue;
    //
    //    private String typeValue;
    private String query;

    /**
     * 向量检索附加等值条件（metadata 字段名 -> 值），字段名需与 {@link cn.fish.knowledge.constants.DocumentMetadataConstant}
     * 及 {@link cn.fish.knowledge.converter.DocumentConverter} 写入的 metadata key 一致。
     * 与 {@code datasourceId}、{@code id}、{@code type} 等 VO 字段自动映射的条件做 AND 合并；已占用的 key 不会重复追加。
     */
    private Map<String, Object> vectorMetadataEq;

    public String getEmbeddingStatusValue() {
        return EmbeddingStatus.getValueByCode(getEmbeddingStatus());
    }

    public String getTypeValue() {
        return KnowledgeType.getValueByCode(getType());
    }

    public String getSplitterTypeValue() {
        return SplitterType.getValueByCode(getSplitterType());
    }
}
