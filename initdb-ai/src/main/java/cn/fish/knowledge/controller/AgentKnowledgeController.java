package cn.fish.knowledge.controller;

import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.controller.RequestUtil;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.knowledge.entity.AgentKnowledgeDTO;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import cn.fish.knowledge.service.AgentKnowledgeService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/agentKnowledge")
public class AgentKnowledgeController extends BaseController {

    private final AgentKnowledgeService agentKnowledgeService;

    public AgentKnowledgeController(AgentKnowledgeService agentKnowledgeService) {
        this.agentKnowledgeService = agentKnowledgeService;
    }

    @GetMapping("/query/page")
    public ResponseResult<Page<AgentKnowledgeVO>> queryPage(@RequestParam Map<String, Object> request) {
        AgentKnowledgeVO vo = RequestUtil.getObject(request, AgentKnowledgeVO.class);
        return result(agentKnowledgeService.queryPage(vo, getIPage(request)));
    }

    @GetMapping("/query/unique")
    public ResponseResult<AgentKnowledgeVO> queryUnique(@RequestParam Map<String, Object> request) {
        AgentKnowledgeVO vo = RequestUtil.getObject(request, AgentKnowledgeVO.class);
        return result(agentKnowledgeService.queryUnique(vo));
    }

    @PostMapping(value = "/add")
    public ResponseResult<Void> add(@RequestPart("datasourceId") String datasourceId,
                                    @RequestPart("title") String title, @RequestPart("type") String type,
                                    @RequestPart(value = "question", required = false) String question,
                                    @RequestPart(value = "content", required = false) String content,
                                    @RequestPart(value = "file", required = false) MultipartFile file, // todo 可修改为 FilePart 调整为响应式
                                    @RequestPart(value = "splitterType", required = false) String splitterType) {
        // 要处理文件必须这样 不能用对象
        AgentKnowledgeDTO dto = new AgentKnowledgeDTO(null, datasourceId, title, type, question, content, file, splitterType);
        agentKnowledgeService.add(dto);
        return result();
    }

    @PutMapping(value = "/update")
    public ResponseResult<Void> update(@RequestBody AgentKnowledgeDTO agentKnowledgeDTO) {
        agentKnowledgeService.update(agentKnowledgeDTO);
        return result();
    }

    @DeleteMapping(value = "/delete")
    public ResponseResult<Void> delete(@RequestBody AgentKnowledgeDTO agentKnowledgeDTO) {
        agentKnowledgeService.delete(agentKnowledgeDTO);
        return result();
    }

    @DeleteMapping(value = "/delete/batch")
    public ResponseResult<Void> deleteBatch(@RequestBody List<AgentKnowledgeDTO> agentKnowledgeDTOList) {
        agentKnowledgeService.delete(agentKnowledgeDTOList);
        return result();
    }

    @PostMapping(value = "/refresh")
    public ResponseResult<Void> refresh(@RequestBody AgentKnowledgeDTO agentKnowledgeDTO) {
        agentKnowledgeService.refresh(List.of(agentKnowledgeDTO));
        return result();
    }

    @PostMapping(value = "/refresh/batch")
    public ResponseResult<Void> refreshBatch(@RequestBody List<AgentKnowledgeDTO> agentKnowledgeDTOList) {
        agentKnowledgeService.refresh(agentKnowledgeDTOList);
        return result();
    }
}
