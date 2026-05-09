package cn.fish.chart.controller;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.service.ChatSessionService;
import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.response.ResponseResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController extends BaseController {

    private final ChatSessionService chatSessionService;

    public ChatController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    @GetMapping("/query/list")
    public ResponseResult<List<ChatSession>> queryList() {
        List<ChatSession> chatSessions = chatSessionService.queryList(new ChatSession());
        return result(chatSessions);
    }

    @PostMapping("/create")
    public ResponseResult<ChatSession> create(@RequestBody ChatSession chatSession) {
        ChatSession add = chatSessionService.add(chatSession);
        return result(add);
    }


    @DeleteMapping("/delete")
    public ResponseResult<Void> delete(@RequestBody ChatSession chatSession) {
        chatSessionService.delete(chatSession);
        chatSession.setSessionId(null);
        return result();
    }


    @DeleteMapping("/delete/all")
    public ResponseResult<Void> deleteAll() {
        chatSessionService.deleteAll();
        return result();
    }
}
