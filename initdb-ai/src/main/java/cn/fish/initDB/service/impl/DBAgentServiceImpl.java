package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.event.ChartAutoSummarizeEvent;
import cn.fish.initDB.service.DBAgentService;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Objects;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

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
            """;
    private final ReactAgent dbAgent;
    private final ContextualizeServiceImpl contextualizeService;
    private final ApplicationEventPublisher eventPublisher;

    public DBAgentServiceImpl(ChatModel chatModel, GetAllTablesTool getAllTablesTool,
                              GetTableSchemaTool getTableSchemaTool, QuerySqlCheckTool querySqlCheckTool, GetTableDataTool getTableDataTool,
                              KnowledgeRetrievalTool knowledgeRetrievalTool, MemorySaver memorySaver,
                              ContextualizeServiceImpl dbAgentContextualizeService, ApplicationEventPublisher eventPublisher) {
        this.contextualizeService = dbAgentContextualizeService;
        this.eventPublisher = eventPublisher;
        this.dbAgent = ReactAgent.builder()
                                 .name("数据库智能体")          // 名称
                                 .systemPrompt(SYSTEM_PROMPT) //提示词
                                 .description(DESCRIPTION) //描述
                                 .model(chatModel)
                                 .saver(memorySaver)
                                 .maxParallelTools(2)
                                 .enableLogging(true)
                                 // 设置工具
                                 .tools(getAllTablesTool.toolCallback()
                                         , getTableSchemaTool.toolCallback()
                                         , querySqlCheckTool.toolCallback()
                                         , getTableDataTool.toolCallback()
                                         , knowledgeRetrievalTool.toolCallback()
                                 )
                                 .build();


    }


//    @Override
//    public ChatResponse chat(ChatRequest chatRequest) {
//        String sessionId = chatRequest.getSessionId();
//        if (StrUtil.isEmpty(sessionId)) {
//            throw new CommonException("Sorry, an error occurred: sessionId is null");
//        }
//        try {
//
//            String standalone = contextualizeService.apply(chatRequest);
//            RunnableConfig config = RunnableConfig.builder()
//                                                  .threadId(chatRequest.getSessionId())
//                                                  .mergeReasoningContent(true)
//                                                  .build();
//            NodeOutput result = dbAgent.invokeAndGetOutput(standalone, config).orElse(null);
//            eventPublisher.publishEvent(new ChartAutoSummarizeEvent(this, config));
//            String response = NodeOutputUtil.extractResponse(result);
//            return new ChatResponse(response, sessionId);
//        } catch (Exception e) {
//            throw new CommonException("Sorry, an error occurred: sessionId is null");
//        }
//    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            return Flux.just("Sorry, an error occurred: sessionId is null");
        }
        try {
            String standalone = contextualizeService.apply(chatRequest);
            RunnableConfig config = RunnableConfig.builder()
                                                  .threadId(chatRequest.getSessionId())
                                                  .mergeReasoningContent(true)
                                                  .build();
            Flux<NodeOutput> stream = dbAgent.stream(standalone, config);
            return stream
                    .doOnComplete(() -> eventPublisher.publishEvent(new ChartAutoSummarizeEvent(this, config)))
                    .filter(nodeOutput -> !nodeOutput.isSTART() && !nodeOutput.isEND()) // 过滤掉开始和结束事件
                    .map(nodeOutput -> {
                        if (nodeOutput instanceof StreamingOutput<?> streamingOutput) {
                            // 翻倍输出问题处理
                            if (streamingOutput.getOutputType().equals(OutputType.AGENT_MODEL_FINISHED)) {
                                return "";
                            }
                            return Objects.requireNonNullElse(streamingOutput.message().getText(), "");
                        }
                        return "";
                    })
                    .filter(StringUtils::hasText)
                    .switchIfEmpty(Flux.defer(() -> {
                        log.warn("chatStream emitted no text for sessionId={}, model may have returned empty tokens", sessionId);
                        return Flux.just("未收到模型的文本输出，请重试。若多次出现，请检查模型 API、配额及网络；"
                                + "若刚做过对话压缩，可新建会话再试。");
                    }))
                    .onErrorResume(e -> {
                        if (e instanceof IllegalStateException && e.getMessage() != null
                                && e.getMessage().contains("Empty flux detected")) {
                            log.warn("LLM stream empty for sessionId={}: {}", sessionId, e.getMessage());
                            return Flux.just("模型流式通道无有效内容（API 无结果、内容审核或上下文异常等）。请稍后重试，或新建会话。"
                                    + " 详情：" + e.getMessage());
                        }
                        log.error("chatStream failed sessionId={}", sessionId, e);
                        return Flux.just("对话生成失败: " + e.getMessage());
                    });
        } catch (Exception e) {
            return Flux.just("Sorry, an error occurred: sessionId is null");
        }
    }

//    @Override
    //    @McpTool(description = "获取会话中的数据库相关信息")
    //    public String getDBChart(@McpToolParam(description = "message") String message, @McpToolParam(description = "Session id") String
    //            sessionId) {
    //        return chat(new ChatRequest(message, sessionId)).getResponse();
    //    }

}
