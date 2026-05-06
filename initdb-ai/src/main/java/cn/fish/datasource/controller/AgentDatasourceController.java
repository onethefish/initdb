package cn.fish.datasource.controller;

import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.controller.RequestUtil;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.service.AgentDatasourceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datasource")
public class AgentDatasourceController extends BaseController {

    private final AgentDatasourceService agentDatasourceService;

    public AgentDatasourceController(AgentDatasourceService agentDatasourceService) {
        this.agentDatasourceService = agentDatasourceService;
    }

    @GetMapping("/query/page")
    public ResponseResult<Page<AgentDatasource>> queryPage(@RequestParam Map<String, Object> request) {
        AgentDatasource agentDatasource = RequestUtil.getObject(request, AgentDatasource.class);
        return result(agentDatasourceService.queryPage(agentDatasource, getIPage(request)));
    }

    @GetMapping("/query/unique")
    public ResponseResult<AgentDatasource> queryUnique(@RequestParam Map<String, Object> request) {
        AgentDatasource agentDatasource = RequestUtil.getObject(request, AgentDatasource.class);
        return result(agentDatasourceService.queryUnique(agentDatasource));
    }

    @PostMapping("/test")
    public ResponseResult<AgentDatasource> test(@RequestBody AgentDatasource agentDatasource) {
        return result(agentDatasourceService.test(agentDatasource));
    }

    @PostMapping("/add")
    public ResponseResult<Void> add(@RequestBody AgentDatasource agentDatasource) {
        agentDatasourceService.add(agentDatasource);
        return result();
    }


    @PutMapping("/update")
    public ResponseResult<Void> update(@RequestBody AgentDatasource agentDatasource) {
        agentDatasourceService.update(agentDatasource);
        return result();
    }

    @DeleteMapping("/delete")
    public ResponseResult<Void> delete(@RequestBody AgentDatasource agentDatasource) {
        agentDatasourceService.delete(agentDatasource);
        return result();
    }

    @DeleteMapping("/delete/batch")
    public ResponseResult<Void> deleteBatch(@RequestBody List<AgentDatasource> agentDatasourceList) {
        agentDatasourceService.delete(agentDatasourceList);
        return result();
    }
}
