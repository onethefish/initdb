package cn.fish.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String root() {
        return "redirect:/datasource";
    }

    @GetMapping("/datasource")
    public String datasource() {
        return "datasource";
    }

    @GetMapping("/chat")
    public String chat() {
        return "chat";
    }

    @GetMapping("/knowledge")
    public String knowledge() {
        return "knowledge";
    }

    @GetMapping("/index")
    public String legacyIndex() {
        return "redirect:/datasource";
    }
}
