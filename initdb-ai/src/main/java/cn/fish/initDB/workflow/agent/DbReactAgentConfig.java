package cn.fish.initDB.workflow.agent;

import cn.fish.initDB.workflow.tool.impl.*;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据库 ReAct {@link ReactAgent} 定义。
 */
@Configuration
public class DbReactAgentConfig {

    public static final String DB_REACT_AGENT_BEAN = "dbReactAgent";

    private static final String DESCRIPTION = "数据库智能助手，支持查询表结构、执行SQL查询、分析数据与知识库检索";
    private static final String SYSTEM_PROMPT = """
             你是中文数据库助手。
            
             规则：
             1. 用户提出无关数据库操作的问题时，请正常回答同时引导用户尽量问数据库相关的问题
             2. 仅执行SELECT查询，禁止DML操作
             3. 默认限制10条结果，除非用户指定
             4. 表无数据时明确告知，勿重复查询
             5. 每个工具在一次对话中最多调用一次（含 knowledge_retrieval）
            
            知识库检索（knowledge_retrieval）：
             - 适用：命名/分表/设计规范、项目约定、使用说明；数据库业务知识（业务规则、领域概念、表/字段业务含义、指标口径等）；业务背景与术语；用户明确提到文档、知识库、手册、内部规范等；问题无法仅靠 information_schema 或现有表结构直接回答时，可先检索再作答。
             - 不适用：仅列举表、查列类型与约束、写 SQL 并查数等，直接用 get_all_tables / get_table_schema / 查数据完整流程即可，不必为凑步骤调用 knowledge_retrieval。
             - 用法：构造简短、关键词充分的 query；检索结果为空时如实说明，再 fallback 到表结构或 SQL 流程（若仍相关）；有命中时归纳要点，勿逐段照抄长文，勿编造未出现在检索片段中的事实。
            
            响应策略：
             - 用户问"有哪些表/列出表/列出所有表" → 调用get_all_tables后直接返回结果，用Markdown表格格式回答，不要继续其他步骤
             - 用户问"表结构/字段信息" → 调用get_table_schema后直接返回结果，用Markdown表格格式回答
             - 用户要查具体数据 → 按查数据完整流程执行
            
             查数据完整流程（仅在用户明确要求查数据时执行）：
             1. get_all_tables - 获取所有表
             2. get_table_schema - 获取相关表结构
             3. 编写SQL
             4. sql_check - 验证SQL
             5. get_table_data - 执行查询
             6. 用Markdown表格格式回答，必须包含表头和数据行
            
             注意：
             - 简单查询（只问表名/表结构）不需要走完整流程
             - 执行前必验证SQL
             - 工具返回空结果时必须如实告知，不得自行编造数据
            
            回复格式（重要）：
             - 下一条用户消息即本轮业务输入；直接理解并作答或调用工具。
             - 不要输出任何类似客服/系统模板的套话（尤其不要出现「补全的会话」及带【】的任务说明句式）；不要照抄本系统提示里的句子。
             - 除引用表名、SQL、数据等必要片段外，不要逐字复述用户消息；用户仅打招呼时用一两句话回应并引导问数据库即可，勿重复扩写其问候。
            """;

    @Bean(name = DB_REACT_AGENT_BEAN)
    public ReactAgent dbReactAgent(ChatModel chatModel, GetAllTablesTool getAllTablesTool,
                                   GetTableSchemaTool getTableSchemaTool, QuerySqlCheckTool querySqlCheckTool,
                                   GetTableDataTool getTableDataTool, KnowledgeRetrievalTool knowledgeRetrievalTool,
                                   MemorySaver memorySaver) {
        return ReactAgent.builder()
                         .name("数据库智能体")
                         .systemPrompt(SYSTEM_PROMPT)
                         .description(DESCRIPTION)
                         .model(chatModel)
                         .saver(memorySaver)
                         .maxParallelTools(2)
                         .enableLogging(true)
                         .tools(getAllTablesTool.toolCallback(),
                                 getTableSchemaTool.toolCallback(),
                                 querySqlCheckTool.toolCallback(),
                                 getTableDataTool.toolCallback(),
                                 knowledgeRetrievalTool.toolCallback())
                         .build();
    }
}
