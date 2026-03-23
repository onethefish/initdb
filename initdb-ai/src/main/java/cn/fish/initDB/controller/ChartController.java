package cn.fish.initDB.controller;

import cn.fish.initDB.entity.ChartSession;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.service.ChartSessionService;
import cn.hutool.core.util.IdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Slf4j
@Controller
@RequestMapping("/chart")
public class ChartController {

    @Autowired
    private ChartSessionService chartSessionService;

    @PostMapping("/create")
    @ResponseBody
    public ChartSession create(@RequestBody ChartSession chartSession) {
        return chartSessionService.add(chartSession);
    }

}
