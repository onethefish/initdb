package cn.fish.initDB.controller;

import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.service.ContextualizeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/db")
public class ContextualizeController extends BaseController {

    private final ContextualizeService contextualizeService;

    public ContextualizeController(ContextualizeService contextualizeService) {
        this.contextualizeService = contextualizeService;
    }

    @PostMapping(path = "/chat/contextualize")
    public ResponseResult<String> contextualize(@RequestBody ChatRequest chatRequest) {
        return result(contextualizeService.rewrite(chatRequest.getMessage(), chatRequest.getSessionId()));
    }

}
