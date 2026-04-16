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
import cn.fish.initDB.service.DBAgentService;
import cn.fish.web.response.ResponseResult;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/db")
public class DBAgentController {

    private final DBAgentService dbAgentService;

    public DBAgentController(DBAgentService dbAgentService) {
        this.dbAgentService = dbAgentService;
    }


    @PostMapping("/chat")
    public ResponseResult<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        ChatResponse chat = dbAgentService.chat(chatRequest);
        return ResponseResult.success(chat);
    }

    @PostMapping(value = "/chat/stream")
    public Flux<String> chatStream(@RequestBody ChatRequest chatRequest) {
        return dbAgentService.chatStream(chatRequest);
    }


    @GetMapping("/chat")
    public ResponseResult<ChatResponse> chatGet(@RequestParam("message") String message,
                                                @RequestParam(value = "sessionId", required = false) String sessionId) {
        ChatResponse chat = dbAgentService.chat(new ChatRequest(message, sessionId));
        return ResponseResult.success(chat);
    }


}
