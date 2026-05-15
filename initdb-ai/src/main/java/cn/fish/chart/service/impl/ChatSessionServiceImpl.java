package cn.fish.chart.service.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.chart.service.ChatSessionService;
import cn.fish.common.savers.CheckpointSessionTreeReleasable;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.database.repository.DataBaseRepository;
import cn.fish.database.service.DataBaseService;
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
    private final DataBaseService dataBaseService;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository, DataBaseRepository dataBaseRepository,
                                  AgentDatasourceRepository agentDatasourceRepository,
                                  BaseCheckpointSaver baseCheckpointSaver,
                                  DataBaseService dataBaseService) {
        this.chatSessionRepository = chatSessionRepository;
        this.dataBaseRepository = dataBaseRepository;
        this.agentDatasourceRepository = agentDatasourceRepository;
        this.baseCheckpointSaver = baseCheckpointSaver;
        this.dataBaseService = dataBaseService;
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
        dataBaseService.invalidateMetadataCache(chatSession.getSessionId());
        chatSessionRepository.remove(chatSession);
        dataBaseRepository.remove(chatSession.getSessionId());
        releaseCheckpointThreadsQuietly(chatSession.getSessionId());
    }

    @Override
    public void deleteAll() {
        List<ChatSession> chatSessions = chatSessionRepository.queryList(new ChatSession());
        for (ChatSession chatSession : chatSessions) {
            releaseCheckpointThreadsQuietly(chatSession.getSessionId());
        }
        dataBaseService.invalidateAllMetadataCaches();
        chatSessionRepository.remove(chatSessions);
        dataBaseRepository.removeAll();
    }

    /**
     * 若 saver 实现 {@link CheckpointSessionTreeReleasable}，则按业务会话释放其下全部相关 thread（含子图等）；
     * 否则仅对 {@code sessionId} 调用一次 {@link BaseCheckpointSaver#release}。
     */
    private void releaseCheckpointThreadsQuietly(String sessionId) {
        if (StrUtil.isBlank(sessionId)) {
            return;
        }
        try {
            if (baseCheckpointSaver instanceof CheckpointSessionTreeReleasable tree) {
                tree.releaseSessionTree(sessionId);
            } else {
                baseCheckpointSaver.release(RunnableConfig.builder().threadId(sessionId).build());
            }
        } catch (Exception ignored) {
        }
    }
}
