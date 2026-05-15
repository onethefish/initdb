package cn.fish.database.service;

import cn.fish.database.dto.DataSqlQueryRequest;
import cn.fish.database.dto.DataSqlValidateRequest;
import cn.fish.database.dto.DataSqlValidateResponse;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Map;

/**
 * 会话内只读 SQL 分页与校验（与导出使用同一套 SQL 守卫）。
 */
public interface DataBaseDataService {

    Page<Map<String, Object>> queryPage(DataSqlQueryRequest request);

    DataSqlValidateResponse validate(DataSqlValidateRequest request);
}
