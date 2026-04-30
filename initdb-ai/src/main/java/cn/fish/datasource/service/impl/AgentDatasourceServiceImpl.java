package cn.fish.datasource.service.impl;

import cn.fish.database.repository.DataBaseRepository;
import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.fish.datasource.service.AgentDatasourceService;
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
    public AgentDatasource queryUnique(AgentDatasource agentDatasource) {
        return agentDatasourceRepository.getById(agentDatasource.getId());
    }

    @Override
    public Page<AgentDatasource> queryPage(AgentDatasource agentDatasource, Page<AgentDatasource> page) {
        return agentDatasourceRepository.queryPage(agentDatasource, page);
    }

    @Override
    public void test(AgentDatasource agentDatasource) {
        // todo
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
