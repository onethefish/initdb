package cn.fish.web.form;

import cn.fish.knowledge.enums.KnowledgeType;
import cn.fish.knowledge.enums.SplitterType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author 余康云
 * @since 2026/5/5 16:43
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeForm {

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
     * 分块策略类型
     *
     * @see SplitterType
     */
    private String splitterType;
}
