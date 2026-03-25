package cn.fish.initDB.controller;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.service.ChatSessionService;
import cn.fish.web.response.ResponseResult;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/chat")
public class ChartController {

    private final ChatSessionService chatSessionService;

    public ChartController(ChatSessionService chatSessionService) {
        this.chatSessionService = chatSessionService;
    }

    // 重定向到聊天首页
    @GetMapping("/")
    public String index() {
        return "index";
    }

    @ResponseBody
    @PostMapping("/create")
    public ResponseResult<ChatSession> create(@RequestBody ChatSession chatSession) {
        ChatSession add = chatSessionService.add(chatSession);
        return ResponseResult.success(add);
    }

    @ResponseBody
    @PostMapping("/delete")
    public ResponseResult<Void> delete(@RequestBody ChatSession chatSession) {
        chatSessionService.delete(chatSession);
        chatSession.setSessionId(null);
        return ResponseResult.success();
    }

    @ResponseBody
    @PostMapping("/delete/all")
    public ResponseResult<Void> deleteAll() {
        chatSessionService.deleteAll();
        return ResponseResult.success();
    }
}
