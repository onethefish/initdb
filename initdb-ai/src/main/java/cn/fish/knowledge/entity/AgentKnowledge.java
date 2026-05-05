package cn.fish.knowledge.entity;

import cn.fish.knowledge.enums.EmbeddingStatus;
import cn.fish.knowledge.info.KnowledgeInfo;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 智能体知识
 */
@Setter
@Getter
@Accessors(chain = true)
public class AgentKnowledge {


    /**
     * 知识ID（主键）
     */
    private String knowledgeId;


    /**
     * 向量化状态
     *
     * @see EmbeddingStatus
     */
    private Integer embeddingStatus;


    /**
     * 知识库内容
     *
     */
    private KnowledgeInfo knowledgeInfo;


    public void uploading() {
        this.embeddingStatus = EmbeddingStatus.PROCESSING.getValue();

    }

    public void fail(String message) {
        this.embeddingStatus = EmbeddingStatus.FAILED.getValue();
        this.knowledgeInfo.setErrorMsg(message);
    }

    public void complete() {
        this.embeddingStatus = EmbeddingStatus.COMPLETED.getValue();
    }

    public void init() {
        if (knowledgeInfo.getIsRecall() == 1) {
            this.embeddingStatus = EmbeddingStatus.PENDING.getValue();
        } else {
            this.embeddingStatus = EmbeddingStatus.COMPLETED.getValue();
        }

    }
}
