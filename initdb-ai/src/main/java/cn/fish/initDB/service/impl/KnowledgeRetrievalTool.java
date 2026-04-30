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
package cn.fish.initDB.service.impl;

import cn.fish.knowledge.repository.VectorStoreRepository;
import cn.fish.initDB.service.AgentAbstractTool;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 用户知识检索工具
 */
@Slf4j
@Component
public class KnowledgeRetrievalTool extends AgentAbstractTool implements BiFunction<KnowledgeRetrievalTool.Request, ToolContext, List<Document>> {

    private static final int DEFAULT_TOP_K = 4;
    @Autowired
    private VectorStoreRepository vectorStoreRepository;

    @Override
    public List<Document> apply(Request request, ToolContext toolContext) {
        log.info("KnowledgeRetrievalTool::apply");
        String sessionId = getSessionId(toolContext);
        int topK = request.topK() != null ? request.topK() : DEFAULT_TOP_K;

        FilterExpressionBuilder filterExpressionBuilder = new FilterExpressionBuilder();
        Filter.Expression expression = filterExpressionBuilder.eq("sessionId", sessionId)
                                                              .build();
        SearchRequest searchRequest = SearchRequest.builder()
                                                   .query(request.query())
                                                   .topK(topK)
                                                   .filterExpression(expression)
                                                   .build();
        // 2. 执行相似性搜索
        return vectorStoreRepository.queryList(searchRequest);
    }


    public ToolCallback toolCallback() {
        return FunctionToolCallback.builder("knowledge_retrieval", this)
                                   .description(
                                           "从用户上传的知识库中检索相关信息。当你需要回答关于当前数据库相关的的问题（例如数据库设计、数据库使用、数据库命名规范、分表设计）时，请使用此工具。该工具会执行语义搜索，根据查询内容查找最相关的技术文档。")
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("来自文档的知识检索请求")
    public record Request(@JsonProperty(value = "query", required = true)
                          @JsonPropertyDescription("用于查找相关文档的搜索查询。请尽量具体，并包含与您的问题相关的关键词。") String query,

                          @JsonProperty(value = "top_k")
                          @JsonPropertyDescription("要检索的顶部结果数量（默认值：4）") Integer topK) {
    }

}
