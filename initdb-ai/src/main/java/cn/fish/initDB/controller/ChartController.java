package cn.fish.initDB.controller;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.service.ChatSessionService;
import cn.fish.web.response.ResponseResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChartController {

    private final ChatSessionService chatSessionService;

    public ChartController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
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
