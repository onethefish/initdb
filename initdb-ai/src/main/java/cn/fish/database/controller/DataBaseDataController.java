package cn.fish.database.controller;

import cn.fish.cloud.serva.web.controller.BaseController;
import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.database.dto.DataSqlQueryRequest;
import cn.fish.database.dto.DataSqlValidateRequest;
import cn.fish.database.dto.DataSqlValidateResponse;
import cn.fish.database.service.DataBaseDataQueryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/dataBase/data")
public class DataBaseDataController extends BaseController {

    private final DataBaseDataQueryService dataBaseDataQueryService;

    public DataBaseDataController(DataBaseDataQueryService dataBaseDataQueryService) {
        this.dataBaseDataQueryService = dataBaseDataQueryService;
    }

    /**
     * 分页执行只读 SQL（会话绑定数据源）；返回 MyBatis-Page 结构，与数据源分页列表等一致。
     */
    @PostMapping("/query/page")
    public ResponseResult<Page<Map<String, Object>>> queryPage(@Valid @RequestBody DataSqlQueryRequest request) {
        return result(dataBaseDataQueryService.queryPage(request));
    }

    /**
     * 仅校验 SQL 是否允许执行（语法预检 + 与导出一致的守卫），不执行查询。
     */
    @PostMapping({"/query/validate", "/validate"})
    public ResponseResult<DataSqlValidateResponse> validate(@Valid @RequestBody DataSqlValidateRequest request) {
        return result(dataBaseDataQueryService.validate(request));
    }
}
