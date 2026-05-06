package cn.fish.datasource.service.impl;

import cn.fish.database.repository.DataBaseRepository;
import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.fish.datasource.service.AgentDatasourceService;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class AgentDatasourceServiceImpl implements AgentDatasourceService {

    private final AgentDatasourceRepository agentDatasourceRepository;
    private final DataBaseRepository dataBaseRepository;


    @Override
    public Page<AgentDatasource> queryPage(AgentDatasource agentDatasource, Page<AgentDatasource> page) {
        return agentDatasourceRepository.queryPage(agentDatasource, page);
    }

    @Override
    public AgentDatasource queryUnique(AgentDatasource agentDatasource) {
        return agentDatasourceRepository.getById(agentDatasource.getId());
    }

    @Override
    public AgentDatasource test(AgentDatasource agentDatasource) {
        dataBaseRepository.test(agentDatasource.getConnectionUrl(), agentDatasource.getUsername(), agentDatasource.getPassword());
        agentDatasource.setTestStatus(1);
        AgentDatasource byId = agentDatasourceRepository.getById(agentDatasource.getId());
        if (ObjectUtil.isNotEmpty(byId)) {
            agentDatasourceRepository.updateById(agentDatasource);
        }
        return agentDatasource;
    }

    @Override
    public void add(AgentDatasource agentDatasource) {
        agentDatasourceRepository.save(agentDatasource);
    }

    @Override
    public void update(AgentDatasource agentDatasource) {
        agentDatasourceRepository.updateById(agentDatasource);
    }

    @Override
    public void delete(AgentDatasource agentDatasource) {
        agentDatasourceRepository.removeById(agentDatasource);
    }

    @Override
    public void delete(List<AgentDatasource> agentDatasourceList) {
        agentDatasourceRepository.removeByIds(agentDatasourceList);
    }
}
