package cn.fish.knowledge.business;

import cn.fish.knowledge.dul.AgentKnowledgePo;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.enums.EmbeddingStatus;
import cn.fish.knowledge.enums.KnowledgeType;
import cn.fish.knowledge.enums.SplitterType;
import cn.fish.knowledge.info.KnowledgeInfo;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * @author 余康云
 * @since 2026/5/5 16:41
 */
@Setter
@Getter
@Accessors(chain = true)
public class KnowledgeBo {

    /**
     * 知识ID（主键）
     */
    private String knowledgeId;

    /**
     * 数据源ID
     */
    private String datasourceId;

    /**
     * 文档标题
     */
    private String title;

    /**
     * 知识类型
     *
     * @see KnowledgeType
     */
    private String type;

    /**
     * 问题内容（FAQ/QA类型时使用）
     */
    private String question;

    /**
     * 答案内容（QA/FAQ类型时使用）
     */
    private String content;

    /**
     * 业务状态：1=召回（该知识参与向量检索，会被AI助手检索到）, 0=非召回（该知识不参与检索，仅存档）
     */
    private Integer isRecall;

    /**
     * 向量化状态
     *
     * @see EmbeddingStatus
     */
    private Integer embeddingStatus;

    /**
     * 错误信息（操作失败时记录）
     */
    private String errorMsg;

    /**
     * 文件ID
     */
    private String fileId;

    /**
     * 文件大小（字节）
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 分块策略类型
     *
     * @see SplitterType
     */
    private String splitterType;

    public void standard() {

    }

    public AgentKnowledge assemble() {
        KnowledgeInfo info = new KnowledgeInfo();
        info.setDatasourceId(this.datasourceId)
        	.setTitle(this.title)
        	.setType(this.type)
        	.setQuestion(this.question)
        	.setContent(this.content)
        	.setIsRecall(this.isRecall)
        	.setEmbeddingStatus(this.embeddingStatus)
        	.setErrorMsg(this.errorMsg)
        	.setFileId(this.fileId)
        	.setFileSize(this.fileSize)
        	.setFileType(this.fileType)
        	.setSplitterType(this.splitterType);

        AgentKnowledge knowledge = new AgentKnowledge();
        knowledge.setKnowledgeInfo(info);
        return knowledge;
    }
}
