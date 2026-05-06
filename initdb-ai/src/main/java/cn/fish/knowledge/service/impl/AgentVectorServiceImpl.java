package cn.fish.knowledge.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.knowledge.constants.DocumentMetadataConstant;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import cn.fish.knowledge.repository.VectorStoreRepository;
import cn.fish.knowledge.service.AgentVectorService;
import cn.hutool.core.util.StrUtil;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AgentVectorServiceImpl implements AgentVectorService {


    private final VectorStoreRepository vectorStoreRepository;

    public AgentVectorServiceImpl(VectorStoreRepository vectorStoreRepository) {
        this.vectorStoreRepository = vectorStoreRepository;
    }

    @Override
    public List<Document> queryList(AgentKnowledgeVO vo) {
        if (StrUtil.isBlank(vo.getDatasourceId())) {
            throw new CommonException("向量检索必须指定 datasourceId");
        }

        Filter.Expression expression = buildExpression(vo);
        SearchRequest searchRequest = SearchRequest.builder()
                                                   .query(vo.getQuery())
                                                   .topK(1)
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
        if (extra != null && !extra.isEmpty()) {
            for (Map.Entry<String, Object> e : extra.entrySet()) {
                String key = e.getKey();
                if (StrUtil.isBlank(key) || usedKeys.contains(key)) {
                    continue;
                }
                Object value = e.getValue();
                if (value == null) {
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
