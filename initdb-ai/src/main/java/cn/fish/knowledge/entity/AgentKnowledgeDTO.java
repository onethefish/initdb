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
package cn.fish.knowledge.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * 创建知识DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledgeDTO {

    private String id;
    /**
     * 数据源ID
     */
    @NotNull(message = "数据源ID不能为空")
    private String datasourceId;

    /**
     * 知识标题
     */
    @NotBlank(message = "知识标题不能为空")
    private String title;

    /**
     * 知识类型：DOCUMENT, QA, FAQ
     */
    @NotBlank(message = "知识类型不能为空")
    private String type;

    /**
     * 问题（FAQ和QA类型时必填）
     */
    private String question;

    /**
     * 内容（当type=QA, FAQ时必填）
     */
    private String content;

    /**
     * 上传的文件（当type=DOCUMENT时必填）
     */
    private MultipartFile file;

    /**
     * 分块策略类型：token, recursive 默认值是 token
     */
    private String splitterType;

}
