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

import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.entity.AgentKnowledgeDTO;
import cn.fish.knowledge.enums.EmbeddingStatus;
import cn.fish.knowledge.enums.KnowledgeType;

public class AgentKnowledgeConverter {

    private AgentKnowledgeConverter() {

    }

    public static AgentKnowledge toDataBaseEntity(AgentKnowledgeDTO agentKnowledgeDto, String fileId) {
        // 创建AgentKnowledge对象
        AgentKnowledge knowledge = new AgentKnowledge();
        knowledge.setDatasourceId(agentKnowledgeDto.getDatasourceId());
        knowledge.setTitle(agentKnowledgeDto.getTitle());
        knowledge.setType(KnowledgeType.valueOf(agentKnowledgeDto.getType()).getCode());
        knowledge.setQuestion(agentKnowledgeDto.getQuestion());
        knowledge.setContent(agentKnowledgeDto.getContent());
        knowledge.setIsRecall(1); // 默认为召回状态
        knowledge.setEmbeddingStatus(EmbeddingStatus.PENDING.getCode()); // 初始状态为待处理

        // 如果是文档类型，设置文件相关信息
        if (agentKnowledgeDto.getFile() != null && !agentKnowledgeDto.getFile().isEmpty()) {
            knowledge.setFileId(fileId);
            knowledge.setFileSize(agentKnowledgeDto.getFile().getSize());
            knowledge.setFileType(agentKnowledgeDto.getFile().getContentType());
        }
        // 设置分块策略类型
        knowledge.setSplitterType(agentKnowledgeDto.getSplitterType());
        return knowledge;
    }

}
