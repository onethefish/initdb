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
package cn.fish.initDB.controller;

import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.util.NodeOutputUtil;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@Slf4j
@Controller
@RequestMapping("/api/sql")
public class DBAgentController {


    private final ReactAgent sqlAgent;

    public DBAgentController(ReactAgent sqlAgent) {
        this.sqlAgent = sqlAgent;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @PostMapping("/chat")
    @ResponseBody
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());
        String threadId = request.getThreadId();
        if (threadId == null || threadId.isEmpty()) {
            threadId = UUID.randomUUID().toString();
        }
        try {
            RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
            NodeOutput result = sqlAgent.invokeAndGetOutput(request.getMessage(), config).orElse(null);
            String response = NodeOutputUtil.extractResponse(result);
            return new ChatResponse(response, threadId, true);
        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return new ChatResponse("Sorry, an error occurred: " + e.getMessage(), threadId, false);
        }
    }

    @GetMapping("/chat")
    @ResponseBody
    public ChatResponse chatGet(@RequestParam("message") String message,
                                @RequestParam(value = "threadId", required = false) String threadId) {
        return chat(new ChatRequest(message, threadId));
    }


}
