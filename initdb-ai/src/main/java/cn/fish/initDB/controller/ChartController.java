package cn.fish.initDB.controller;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.service.ChatSessionService;
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
    public ChatSession create(@RequestBody ChatSession chatSession) {
        return chatSessionService.add(chatSession);
    }

    @ResponseBody
    @PostMapping("/delete")
    public ChatSession delete(@RequestBody ChatSession chatSession) {
        chatSessionService.delete(chatSession);
        chatSession.setSessionId(null);
        return chatSession;
    }

    @ResponseBody
    @PostMapping("/delete/all")
    public ChatSession deleteAll() {
        chatSessionService.deleteAll();
        return new ChatSession();
    }
}
