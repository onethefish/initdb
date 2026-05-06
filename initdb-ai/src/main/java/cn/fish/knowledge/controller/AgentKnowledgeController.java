package cn.fish.knowledge.controller;

import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.knowledge.entity.AgentKnowledgeDTO;
import cn.fish.knowledge.service.AgentKnowledgeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;


@RestController
@RequestMapping("/agentKnowledge")
public class AgentKnowledgeController extends BaseController {

    private final AgentKnowledgeService agentKnowledgeService;

    public AgentKnowledgeController(AgentKnowledgeService agentKnowledgeService) {
        this.agentKnowledgeService = agentKnowledgeService;
    }


    @PostMapping(value = "/add")
    public ResponseResult<Void> add(@RequestPart("datasourceId") String datasourceId,
                                    @RequestPart("title") String title, @RequestPart("type") String type,
                                    @RequestPart(value = "question", required = false) String question,
                                    @RequestPart(value = "content", required = false) String content,
                                    @RequestPart(value = "file", required = false) MultipartFile file, // todo 可修改为 FilePart 调整为响应式
                                    @RequestPart(value = "splitterType", required = false) String splitterType) {
        // 要处理文件必须这样 不能用对象
        AgentKnowledgeDTO dto = new AgentKnowledgeDTO(datasourceId, title, type, question, content, file, splitterType);
        agentKnowledgeService.add(dto);
        return ResponseResult.success();
    }

}
