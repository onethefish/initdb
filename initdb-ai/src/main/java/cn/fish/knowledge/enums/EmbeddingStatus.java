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
package cn.fish.knowledge.enums;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum EmbeddingStatus {

    PENDING(0, "待处理"),
    PROCESSING(1, "处理中"),
    COMPLETED(2, "已完成"),
    FAILED(3, "失败");

    private final Integer code;
    private final String value;

    EmbeddingStatus(Integer code, String value) {
        this.code = code;
        this.value = value;
    }

    public static String getValueByCode(Integer code) {
        for (EmbeddingStatus status : values()) {
            if (ObjectUtil.equal(status.getCode(), code)) {
                return status.getValue();
            }
        }
        return null;
    }

}
