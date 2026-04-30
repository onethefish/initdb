package cn.fish.datasource.repository.impl;

import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.mapper.AgentDatasourceMapper;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;


@Repository
public class AgentDatasourceRepositoryImpl extends CrudRepository<AgentDatasourceMapper, AgentDatasource> implements AgentDatasourceRepository {

    @Override
    public Page<AgentDatasource> queryPage(AgentDatasource agentDatasource, Page<AgentDatasource> page) {
        LambdaQueryWrapper<AgentDatasource> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(agentDatasource.getName()), AgentDatasource::getName, agentDatasource.getName());
        queryWrapper.like(StrUtil.isNotBlank(agentDatasource.getDescription()), AgentDatasource::getDescription, agentDatasource.getDescription());
        queryWrapper.like(StrUtil.isNotBlank(agentDatasource.getHost()), AgentDatasource::getHost, agentDatasource.getHost());
        return page(page, queryWrapper);
    }
}




