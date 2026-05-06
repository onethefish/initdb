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

import lombok.Getter;

@Getter
public enum SplitterType {

    TOKEN("token","Token分割"),
    RECURSIVE("recursive","递归分割"),
    SENTENCE("sentence","句子分割"),
    PARAGRAPH("paragraph","段落分割"),
    SEMANTIC("semantic","语义分割");
    private final String code;
	private final String value;

	SplitterType(String code, String value) {
        this.code = code;
        this.value = value;
	}

    public static String getValueByCode(String code) {
        for (SplitterType status : values()) {
            if (status.getCode().equals(code)) {
                return status.getValue();
            }
        }
        return null;
    }
}
