/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.fish.knowledge.converter;

import cn.fish.knowledge.constants.DocumentMetadataConstant;
import cn.fish.knowledge.entity.AgentKnowledge;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocumentConverter {


    /**
     * 为文档列表添加元数据，用于DOCUMENT类型知识处理
     *
     * @param documents 原始文档列表
     * @param knowledge 知识对象
     * @return 添加了元数据的文档列表
     */
    public static List<Document> convertAgentKnowledgeDocumentsWithMetadata(List<Document> documents, AgentKnowledge knowledge) {
        List<Document> documentsWithMetadata = new ArrayList<>();

        for (Document doc : documents) {
            // 创建元数据
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put(DocumentMetadataConstant.DATASOURCE_ID, knowledge.getDatasourceId());
            metadata.put(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, knowledge.getId());
            metadata.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE);
            metadata.put(DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE, knowledge.getType());
            // 创建带有元数据的新文档
            Document docWithMetadata = new Document(doc.getId(), doc.getText(), metadata);
            documentsWithMetadata.add(docWithMetadata);
        }
        return documentsWithMetadata;
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private DocumentConverter() {
        throw new AssertionError("Cannot instantiate utility class");
    }

}
