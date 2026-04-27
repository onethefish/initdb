package cn.fish.chart.controller;

import cn.fish.chart.service.ChatSessionService;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatSession;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChartController {

    private final ChatSessionService chatSessionService;

    public ChartController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }


    @PostMapping("/chat/stream")
    public Flux<String> chatStream(@RequestBody ChatRequest chatRequest) {
        return chatSessionService.chatStream(chatRequest);
    }

    @GetMapping("/query/list")
    public ResponseResult<List<ChatSession>> queryList() {
        List<ChatSession> chatSessions = chatSessionService.queryList(null);
        return ResponseResult.success(chatSessions);
    }

    @PostMapping("/create")
    public ResponseResult<ChatSession> create(@RequestBody ChatSession chatSession) {
        ChatSession add = chatSessionService.add(chatSession);
        return ResponseResult.success(add);
    }


    @DeleteMapping("/delete")
    public ResponseResult<Void> delete(@RequestBody ChatSession chatSession) {
        chatSessionService.delete(chatSession);
        chatSession.setSessionId(null);
        return ResponseResult.success();
    }


    @DeleteMapping("/delete/all")
    public ResponseResult<Void> deleteAll() {
        chatSessionService.deleteAll();
        return ResponseResult.success();
    }
}
