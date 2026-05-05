package cn.fish.initDB.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.event.ChartAutoSummarizeEvent;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.util.NodeOutputUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Objects;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

    private static final String DESCRIPTION = "数据库智能助手，支持查询表结构、执行SQL查询、分析数据功能";
    private static final String SYSTEM_PROMPT = """
             你是中文数据库助手。
            
             规则：
             1. 用户提出无关数据库操作的问题时，请正常回答同时引导用户尽量问数据库相关的问题
             2. 仅执行SELECT查询，禁止DML操作
             3. 默认限制10条结果，除非用户指定
             4. 表无数据时明确告知，勿重复查询
             5. 每个工具在一次对话中最多调用一次
            
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
    private final ApplicationEventPublisher eventPublisher;

    public DBAgentServiceImpl(ChatModel chatModel, GetAllTablesTool getAllTablesTool,
                              GetTableSchemaTool getTableSchemaTool, QuerySqlCheckTool querySqlCheckTool, GetTableDataTool getTableDataTool,
                              KnowledgeRetrievalTool knowledgeRetrievalTool, MemorySaver memorySaver, ApplicationEventPublisher eventPublisher) {
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
                                         //                                 , knowledgeRetrievalTool.toolCallback()
                                 )
                                 .build();


    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            throw new CommonException("Sorry, an error occurred: sessionId is null");
        }
        try {
            RunnableConfig config = RunnableConfig.builder()
                                                  .threadId(sessionId)
                                                  .mergeReasoningContent(true)
                                                  .build();
            NodeOutput result = dbAgent.invokeAndGetOutput(chatRequest.getMessage(), config).orElse(null);
            eventPublisher.publishEvent(new ChartAutoSummarizeEvent(this, config));
            String response = NodeOutputUtil.extractResponse(result);
            return new ChatResponse(response, sessionId);
        } catch (Exception e) {
            throw new CommonException("Sorry, an error occurred: sessionId is null");
        }
    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            return Flux.just("Sorry, an error occurred: sessionId is null");
        }
        try {
            RunnableConfig config = RunnableConfig.builder()
                                                  .threadId(sessionId)
                                                  .mergeReasoningContent(true)
                                                  .build();
            Flux<NodeOutput> stream = dbAgent.stream(chatRequest.getMessage(), config);
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

    @Override
    @McpTool(description = "获取会话中的数据库相关信息")
    public String getDBChart(@McpToolParam(description = "message") String message, @McpToolParam(description = "Session id") String
            sessionId) {
        return chat(new ChatRequest(message, sessionId)).getResponse();
    }

}
