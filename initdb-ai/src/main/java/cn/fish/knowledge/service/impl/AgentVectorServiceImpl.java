package cn.fish.knowledge.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.common.prompt.ApplicationPromptTemplates;
import cn.fish.knowledge.constants.DocumentMetadataConstant;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import cn.fish.knowledge.repository.VectorStoreRepository;
import cn.fish.knowledge.service.AgentVectorService;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgentVectorServiceImpl implements AgentVectorService {


    private final VectorStoreRepository vectorStoreRepository;
    private final ChatModel chatModel;
    private final ApplicationPromptTemplates applicationPromptTemplates;

    public AgentVectorServiceImpl(ChatModel chatModel, VectorStoreRepository vectorStoreRepository,
                                  ApplicationPromptTemplates applicationPromptTemplates) {
        this.chatModel = chatModel;
        this.vectorStoreRepository = vectorStoreRepository;
        this.applicationPromptTemplates = applicationPromptTemplates;
    }

    private static final int QUERY_LIST_TOP_K = 1;
    private static final int RAG_TOP_K = 5;
    private static final int TOP_K_MAX = 20;

    @Override
    public List<Document> queryList(AgentKnowledgeVO vo) {
        validateBase(vo);
        return similaritySearch(vo, resolveTopK(vo.getTopK(), QUERY_LIST_TOP_K));
    }

    @Override
    public String rag(AgentKnowledgeVO vo) {
        validateBase(vo);
        List<Document> documents = similaritySearch(vo, resolveTopK(vo.getTopK(), RAG_TOP_K));
        if (CollUtil.isEmpty(documents)) {
            return "未找到相关知识库内容";
        }

        String context = documents.stream()
                                  .map(Document::getText)
                                  .filter(StrUtil::isNotBlank)
                                  .reduce((a, b) -> a + "\n\n" + b)
                                  .orElse("");

        String fullPrompt = applicationPromptTemplates.renderAgentVectorRagAnswer(context, vo.getQuery());

        return chatModel.call(new Prompt(fullPrompt))
                        .getResult()
                        .getOutput()
                        .getText();
    }

    private static void validateBase(AgentKnowledgeVO vo) {
        if (StrUtil.isBlank(vo.getDatasourceId())) {
            throw new CommonException("向量检索必须指定 datasourceId");
        }
    }

    /**
     * 解析 {@link AgentKnowledgeVO#getTopK()}：空或非法时使用 defaultTopK，否则限制在 [1, TOP_K_MAX]。
     */
    private static int resolveTopK(String topKRaw, int defaultTopK) {
        if (StrUtil.isBlank(topKRaw)) {
            return defaultTopK;
        }
        try {
            int k = Integer.parseInt(topKRaw.trim());
            if (k < 1) {
                return defaultTopK;
            }
            return Math.min(k, TOP_K_MAX);
        } catch (NumberFormatException e) {
            return defaultTopK;
        }
    }

    private List<Document> similaritySearch(AgentKnowledgeVO vo, int topK) {
        Filter.Expression expression = buildExpression(vo);
        SearchRequest searchRequest = SearchRequest.builder()
                                                   .query(vo.getQuery())
                                                   .topK(topK)
                                                   .filterExpression(expression)
                                                   .build();
        return vectorStoreRepository.queryList(searchRequest);
    }

    private static Filter.Expression buildExpression(AgentKnowledgeVO vo) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();

        ops.add(b.eq(DocumentMetadataConstant.DATASOURCE_ID, vo.getDatasourceId()));
        usedKeys.add(DocumentMetadataConstant.DATASOURCE_ID);

        if (StrUtil.isNotBlank(vo.getId())) {
            ops.add(b.eq(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, vo.getId()));
            usedKeys.add(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID);
        }
        if (StrUtil.isNotBlank(vo.getType())) {
            ops.add(b.eq(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, vo.getType()));
            usedKeys.add(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);
        }

        Map<String, Object> extra = vo.getVectorMetadataEq();
        if (CollUtil.isNotEmpty(extra)) {
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                String key = e.getKey();
                if (StrUtil.isBlank(key) || usedKeys.contains(key)) {
                    continue;
                }
                Object value = e.getValue();
                if (ObjectUtil.isNull(value)) {
                    continue;
                }
                if (value instanceof String s && StrUtil.isBlank(s)) {
                    continue;
                }
                ops.add(b.eq(key, value));
                usedKeys.add(key);
            }
        }

        FilterExpressionBuilder.Op combined = ops.get(0);
        for (int i = 1; i < ops.size(); i++) {
            combined = b.and(combined, ops.get(i));
        }

        return combined.build();
    }
}
