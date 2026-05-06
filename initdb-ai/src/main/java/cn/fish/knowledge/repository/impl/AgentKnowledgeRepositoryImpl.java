package cn.fish.knowledge.repository.impl;

import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import cn.fish.knowledge.mapper.AgentKnowledgeMapper;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public class AgentKnowledgeRepositoryImpl extends CrudRepository<AgentKnowledgeMapper, AgentKnowledge> implements AgentKnowledgeRepository {

    @Override
    public Page<AgentKnowledge> queryPage(AgentKnowledgeVO vo, Page<AgentKnowledge> page) {
        LambdaQueryWrapper<AgentKnowledge> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentKnowledge::getDatasourceId, vo.getDatasourceId());
        queryWrapper.eq(StrUtil.isNotBlank(vo.getType()), AgentKnowledge::getType, vo.getType());
        queryWrapper.eq(StrUtil.isNotBlank(vo.getSplitterType()), AgentKnowledge::getSplitterType, vo.getSplitterType());
        queryWrapper.eq(ObjectUtil.isNotEmpty(vo.getIsRecall()), AgentKnowledge::getIsRecall, vo.getIsRecall());
        queryWrapper.eq(ObjectUtil.isNotEmpty(vo.getEmbeddingStatus()), AgentKnowledge::getEmbeddingStatus, vo.getEmbeddingStatus());
        queryWrapper.like(StrUtil.isNotBlank(vo.getTitle()), AgentKnowledge::getTitle, vo.getTitle());
        return page(page, queryWrapper);
    }
}
