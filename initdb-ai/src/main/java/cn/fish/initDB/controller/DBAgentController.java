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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping("/db")
public class DBAgentController {

    private final DBAgentService dbAgentService;

    public DBAgentController(DBAgentService dbAgentService) {
        this.dbAgentService = dbAgentService;
    }

    @ResponseBody
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest chatRequest) {
        return dbAgentService.chat(chatRequest);
    }

    @ResponseBody
    @GetMapping("/chat")
    public ChatResponse chatGet(@RequestParam("message") String message,
                                @RequestParam(value = "sessionId", required = false) String sessionId) {
        return chat(new ChatRequest(message, sessionId));
    }


}
