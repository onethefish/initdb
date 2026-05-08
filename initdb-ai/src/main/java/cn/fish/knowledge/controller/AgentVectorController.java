package cn.fish.knowledge.controller;

import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.controller.RequestUtil;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import cn.fish.knowledge.service.AgentVectorService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/agentVector")
public class AgentVectorController extends BaseController {

    private final AgentVectorService agentVectorService;

    public AgentVectorController(AgentVectorService agentVectorService) {
        this.agentVectorService = agentVectorService;
    }

    @GetMapping("/query/list")
    public ResponseResult<List<Document>> queryList(@RequestParam Map<String, Object> request) {
        AgentKnowledgeVO vo = RequestUtil.getObject(request, AgentKnowledgeVO.class);
        return result(agentVectorService.queryList(vo));
    }

    @GetMapping("/query/rag")
    public ResponseResult<String> rag(@RequestParam Map<String, Object> request) {
        AgentKnowledgeVO vo = RequestUtil.getObject(request, AgentKnowledgeVO.class);
        return result(agentVectorService.rag(vo));
    }
}
