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
     * 分块策略类型
     *
     * @see SplitterType
     */
    private String splitterType;
}
