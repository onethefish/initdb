/*
 * Copyright 2026-2027 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.fish.initDB.workflow.tool.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.initDB.workflow.tool.AgentAbstractTool;
import cn.fish.knowledge.constants.DocumentMetadataConstant;
import cn.fish.knowledge.repository.VectorStoreRepository;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 用户知识检索工具
 */
@Slf4j
@Component
public class KnowledgeRetrievalTool extends AgentAbstractTool implements BiFunction<KnowledgeRetrievalTool.Request, ToolContext, List<Document>> {

    private static final int DEFAULT_TOP_K = 1;

    private final VectorStoreRepository vectorStoreRepository;
    private final ChatSessionRepository chatSessionRepository;

    public KnowledgeRetrievalTool(VectorStoreRepository vectorStoreRepository, ChatSessionRepository chatSessionRepository) {
        this.vectorStoreRepository = vectorStoreRepository;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    public List<Document> apply(Request request, ToolContext toolContext) {
        log.info("KnowledgeRetrievalTool::apply");
        String sessionId = getSessionId(toolContext);
        int topK = request.topK() != null ? request.topK() : DEFAULT_TOP_K;
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();
        Filter.Expression expression = filterExpressionBuilder.eq(DocumentMetadataConstant.DATASOURCE_ID, chatSession.getDatasourceId())
                                                              .build();
        SearchRequest searchRequest = SearchRequest.builder()
                                                   .query(request.query())
                                                   .topK(topK)
                                                   .filterExpression(expression)
                                                   .build();
        return vectorStoreRepository.queryList(searchRequest);
    }


    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("knowledge_retrieval", this)
                                   .description(
                                           "从当前数据源关联的知识库中做语义检索。除库表设计、使用说明、命名与分表规范等技术文档外，知识库还可包含数据库业务知识：业务规则、领域概念、表/字段的业务含义、指标与统计口径、流程与协作约定等。当仅靠 information_schema 或表结构元数据不足以回答业务或背景类问题时，应使用本工具；返回若干相关片段，请据此归纳回答，勿编造未出现在片段中的内容。")
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("向知识库发起的语义检索请求")
    public record Request(@JsonProperty(value = "query", required = true)
                          @JsonPropertyDescription("检索用语。尽量具体，可含业务术语、表名、指标名、业务场景或与问题强相关的关键词。") String query,

                          @JsonProperty(value = "top_k")
                          @JsonPropertyDescription("要检索的顶部结果数量（默认值：1）") Integer topK) {
    }

}
