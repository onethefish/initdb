package cn.fish.initDB.controller;

import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import cn.fish.initDB.service.DBAgentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;


@RestController
@RequestMapping("/db")
public class DBAgentController extends BaseController {

    private final DBAgentService dbAgentService;

    public DBAgentController(DBAgentService dbAgentService) {
        this.dbAgentService = dbAgentService;
    }


    @PostMapping("/chat")
    public ResponseResult<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        ChatResponse chat = dbAgentService.chat(chatRequest);
        return result(chat);
    }

    @PostMapping(path = "/chat/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = "text/plain;charset=UTF-8")
    public Flux<String> chatStream(@RequestBody ChatRequest chatRequest) {
        return dbAgentService.chatStream(chatRequest);
    }

    @GetMapping("/chat")
    public ResponseResult<ChatResponse> chatGet(@RequestParam("message") String message,
                                                @RequestParam(value = "sessionId", required = false) String sessionId) {
        ChatResponse chat = dbAgentService.chat(new ChatRequest(message, sessionId));
        return result(chat);
    }


}
