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
package cn.fish.initDB.workflow.agent.tool;

import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.database.service.DataBaseService;
import cn.fish.chart.entity.ChatSession;
import cn.fish.initDB.entity.Table;
import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.BiFunction;

/**
 * 查询表清单工具
 */
@Slf4j
@Component
public class GetAllTablesTool extends AgentAbstractTool implements BiFunction<GetAllTablesTool.Request, ToolContext, String> {


    private final DataBaseService dataBaseService;
    private final ChatSessionRepository chatSessionRepository;

    public GetAllTablesTool(DataBaseService dataBaseService, ChatSessionRepository chatSessionRepository) {
        this.dataBaseService = dataBaseService;
        this.chatSessionRepository = chatSessionRepository;
    }

    @Override
    public String apply(Request request, ToolContext toolContext) {
        log.info("GetAllTablesTool::apply");
        String sessionId = getSessionId(toolContext);
        ChatSession chatSession = chatSessionRepository.queryUnique(sessionId);
        List<Table> tables = dataBaseService.queryTableList(chatSession);
        if (CollUtil.isNotEmpty(tables)) {
            return JSON.toJSONString(tables, JSONWriter.Feature.IgnoreEmpty);
        }
        else {
            return "Error listing tables";
        }
    }


    public ToolCallback toolCallback() {
        String description = "列出数据库中所有可用的表。返回 JSON 数组，每项含 tableName（物理表名，SQL 与 get_table_schema 必须用此字段）与 remarks（中文说明，仅注释不可当表名）。用户用中文称呼表时，须用 remarks 对照后选用对应 tableName。";
        return FunctionToolCallback.builder("get_all_tables", this)
                                   .description(description)
                                   .inputType(Request.class)
                                   .build();
    }

    @JsonClassDescription("请列出所有表")
    public static final class Request {

        @JsonProperty(value = "dummy", required = false)
        @JsonPropertyDescription("这是一个占位参数，不需要传值，请传空字符串或忽略。")
        private final String dummy;

        public Request(
                @JsonProperty(value = "dummy", required = false)
                @JsonPropertyDescription("这是一个占位参数，不需要传值，请传空字符串或忽略。") String dummy) {
            this.dummy = dummy;
        }

        @JsonProperty(value = "dummy", required = false)
        @JsonPropertyDescription("这是一个占位参数，不需要传值，请传空字符串或忽略。")
        public String dummy() {
            return dummy;
        }

        @Override
        public String toString() {
            return "Request[" +
                    "dummy=" + dummy + ']';
        }

    }

}
