package cn.fish.database.controller;

import cn.fish.chart.entity.ChatSession;
import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.controller.RequestUtil;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.database.service.DataBaseService;
import cn.fish.initDB.entity.Table;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dataBase")
public class DataBaseController extends BaseController {

    private final DataBaseService dataBaseService;

    public DataBaseController(DataBaseService dataBaseService) {
        this.dataBaseService = dataBaseService;
    }

    @GetMapping("/query/list")
    public ResponseResult<List<Table>> queryList(@RequestParam Map<String, Object> request) {
        ChatSession chatSession = RequestUtil.getObject(request, ChatSession.class);
        return result(dataBaseService.queryTableList(chatSession));
    }

    @GetMapping("/query/unique")
    public ResponseResult<Table> queryUnique(@RequestParam Map<String, Object> request) {
        ChatSession chatSession = RequestUtil.getObject(request, ChatSession.class);
        String tableName = RequestUtil.getStringTrim(request, "tableName");
        return result(dataBaseService.queryTableSchema(chatSession, tableName));
    }
}
