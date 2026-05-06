package cn.fish.datasource.service;

import cn.fish.datasource.entity.AgentDatasource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public interface AgentDatasourceService {

    Page<AgentDatasource> queryPage(AgentDatasource agentDatasource, Page<AgentDatasource> page);

    AgentDatasource queryUnique(AgentDatasource agentDatasource);

    AgentDatasource test(AgentDatasource agentDatasource);

    void add(AgentDatasource agentDatasource);

    void update(AgentDatasource agentDatasource);

    void delete(AgentDatasource agentDatasource);

    void delete(List<AgentDatasource> agentDatasourceList);
}
