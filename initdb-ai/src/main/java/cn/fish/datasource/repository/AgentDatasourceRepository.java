package cn.fish.datasource.repository;

import cn.fish.datasource.entity.AgentDatasource;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.IRepository;

import java.util.List;

public interface AgentDatasourceRepository extends IRepository<AgentDatasource> {

    Page<AgentDatasource> queryPage(AgentDatasource agentDatasource, Page<AgentDatasource> page);



}
