package cn.fish.chart.service.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.chart.service.ChatSessionService;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.database.repository.DataBaseRepository;
import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final DataBaseRepository dataBaseRepository;
    private final AgentDatasourceRepository agentDatasourceRepository;
    private final BaseCheckpointSaver baseCheckpointSaver;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository, DataBaseRepository dataBaseRepository,
                                  AgentDatasourceRepository agentDatasourceRepository,
                                  BaseCheckpointSaver baseCheckpointSaver) {
        this.chatSessionRepository = chatSessionRepository;
        this.dataBaseRepository = dataBaseRepository;
        this.agentDatasourceRepository = agentDatasourceRepository;
        this.baseCheckpointSaver = baseCheckpointSaver;
    }


    @Override
    public ChatSession add(ChatSession chatSession) {
        chatSession.setSessionId(IdWorker.get32UUID());
        validateDataSource(chatSession);
        chatSessionRepository.add(chatSession);
        return chatSession;
    }

    private void validateDataSource(ChatSession chatSession) {
        if (StrUtil.isBlank(chatSession.getDatasourceId())) {
            throw new CommonException("请选择数据源");
        }
        AgentDatasource ds = agentDatasourceRepository.getById(chatSession.getDatasourceId());
        if (ObjectUtil.isNull(ds)) {
            throw new CommonException("数据源不存在");
        }
        if (!ObjectUtil.equal(1, ds.getStatus()) || !ObjectUtil.equal(1, ds.getTestStatus())) {
            throw new CommonException("仅可选择已启用且连接测试成功的数据源，请先在数据源管理中配置并测试");
        }
        if (StrUtil.isBlank(ds.getConnectionUrl()) || StrUtil.isBlank(ds.getUsername())) {
            throw new CommonException("数据源连接信息不完整");
        }
        dataBaseRepository.add(chatSession.getSessionId(), ds.getConnectionUrl(), ds.getUsername(), ds.getPassword());
    }

    @Override
    public List<ChatSession> queryList(ChatSession chatSession) {
        return chatSessionRepository.queryList(chatSession);
    }

    @Override
    public void delete(ChatSession chatSession) {
        chatSessionRepository.remove(chatSession);
        dataBaseRepository.remove(chatSession.getSessionId());
        try {
            baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
        } catch (Exception ignored) {

        }
    }

    @Override
    public void deleteAll() {
        List<ChatSession> chatSessions = chatSessionRepository.queryList(new ChatSession());
        try {
            for (ChatSession chatSession : chatSessions) {
                baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
            }
        } catch (Exception ignored) {

        }
        chatSessionRepository.remove(chatSessions);
        dataBaseRepository.removeAll();
    }
}
