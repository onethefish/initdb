package cn.fish.initDB.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import cn.fish.initDB.util.NodeOutputUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.Map;

@Slf4j
@Service
public class DBAgentServiceImpl implements DBAgentService {

    private final ReactAgent dbAgent;
    private final ReactAgent summaryAgent;
    private final BaseCheckpointSaver baseCheckpointSaver;
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
             - 用户问"有哪些表/列出表/列出所有表" → 调用get_all_tables后直接返回结果，不要继续其他步骤
             - 用户问"表结构/字段信息" → 调用get_table_schema后直接返回结果
             - 用户要查具体数据 → 按工作流程执行
            
             查数据完整流程（仅在用户明确要求查数据时执行）：
             1. get_all_tables - 获取所有表
             2. get_table_schema - 获取相关表结构
             3. 编写SQL
             4. sql_check - 验证SQL
             5. get_table_data - 执行查询
             6. 用中文回答
            
             注意：
             - 简单查询（只问表名/表结构）不需要走完整流程
             - 执行前必验证SQL
             - 工具返回空结果时必须如实告知，不得自行编造数据
            """;

    private static final String SUMMARY_PROMPT = """
            你是对话总结助手。将以下对话历史总结为简洁的要点:
            - 用户的核心需求
            - 涉及的关键表和字段
            - 重要的SQL查询或结论
            
            要求:
            - 用中文总结
            - 控制在100字以内
            - 保留关键技术细节
            """;

    public DBAgentServiceImpl(BaseCheckpointSaver baseCheckpointSaver, ChatModel chatModel, GetAllTablesTool getAllTablesTool,
                              GetTableSchemaTool getTableSchemaTool, QuerySqlCheckTool querySqlCheckTool, GetTableDataTool getTableDataTool,
                              KnowledgeRetrievalTool knowledgeRetrievalTool, MemorySaver memorySaver) {
        this.baseCheckpointSaver = baseCheckpointSaver;
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

        this.summaryAgent = ReactAgent.builder()
                                      .name("对话总结助手")
                                      .systemPrompt(SUMMARY_PROMPT)
                                      .model(chatModel)
                                      .enableLogging(false)
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
            autoSummarizeIfNeeded(config);
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
                    .doOnComplete(() -> autoSummarizeIfNeeded(config))
                    .filter(nodeOutput -> !nodeOutput.isSTART() && !nodeOutput.isEND()) // 过滤掉开始和结束事件
                    .map(nodeOutput -> {
                        if (nodeOutput instanceof StreamingOutput streamingOutput) {
                            // 翻倍输出问题处理
                            if (streamingOutput.getOutputType().equals(OutputType.AGENT_MODEL_FINISHED)) {
                                return "";
                            }
                            String chunk = streamingOutput.chunk();
                            if (chunk != null) {
                                return chunk;
                            }
                            return "";
                        }
                        else {
                            Map<String, Object> data = nodeOutput.state().data();
                            if (data.containsKey("messages")) {
                                Object messages = data.get("messages");
                                return messages != null ? String.valueOf(messages) : "";
                            }
                            return "";
                        }
                    })
                    .filter(StringUtils::hasText);
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


    private void autoSummarizeIfNeeded(RunnableConfig config) {
        try {
            Collection<Checkpoint> checkpoints = baseCheckpointSaver.list(config);
            if (checkpoints.size() >= 4) {
                StringBuilder conversationHistory = new StringBuilder();
                checkpoints.forEach(checkpoint -> {
                    checkpoint.getState().forEach((key, value) -> {
                        if ("messages".equals(key) && value != null) {
                            conversationHistory.append(value.toString()).append("\n");
                        }
                    });
                });

                if (!conversationHistory.isEmpty()) {
                    summaryAgent.invoke(conversationHistory.toString(), config);
                    log.info("Auto summary generated for session: {}", config.threadId().orElse("unknown"));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to generate auto summary", e);
        }
    }
    //    private void manageCheckpoints(RunnableConfig config) throws Exception {
    //        Collection<Checkpoint> checkpoints = baseCheckpointSaver.list(config);
    //        if (checkpoints.size() > 5) {
    //            baseCheckpointSaver.release(config);
    //        }
    //    }
}
