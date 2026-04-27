package cn.fish.initDB.controller;

import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import org.springframework.web.bind.annotation.*;


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


    @GetMapping("/chat")
    public ResponseResult<ChatResponse> chatGet(@RequestParam("message") String message,
                                                @RequestParam(value = "sessionId", required = false) String sessionId) {
        ChatResponse chat = dbAgentService.chat(new ChatRequest(message, sessionId));
        return ResponseResult.success(chat);
    }


}
