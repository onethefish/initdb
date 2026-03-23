package cn.fish.initDB.controller;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequestMapping("/chat")
public class ChartController {

    @Autowired
    private ChatSessionService chatSessionService;

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

}
