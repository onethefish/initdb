package cn.fish.chart.repository.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.mapper.ChatSessionMapper;
import cn.fish.chart.repository.ChatSessionRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.repository.CrudRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Repository
public class ChatSessionRepositoryImpl extends CrudRepository<ChatSessionMapper, ChatSession> implements ChatSessionRepository {


    private static final Cache<String, ChatSession> CHART_SESSION = Caffeine.newBuilder()
                                                                            .expireAfterAccess(60, TimeUnit.MINUTES)
                                                                            .maximumSize(128) // 最大支持128个会话
                                                                            .build();

    @Override
    public ChatSession queryUnique(String sessionId) {
        return CHART_SESSION.get(sessionId, v -> getById(sessionId));
    }

    @Override
    public List<ChatSession> queryList(ChatSession chatSession) {
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.isNotBlank(chatSession.getSessionName()), ChatSession::getSessionName, chatSession.getSessionName());
        return list(queryWrapper);
    }

    @Override
    public void add(ChatSession chatSession) {
        save(chatSession);
    }

    @Override
    public void remove(ChatSession chatSession) {
        CHART_SESSION.invalidate(chatSession.getSessionId());
        removeById(chatSession);
    }

    @Override
    public void remove(List<ChatSession> chatSessions) {
        removeByIds(chatSessions);
        for (ChatSession chatSession : chatSessions) {
            CHART_SESSION.invalidate(chatSession.getSessionId());
        }
    }

    @Override
    public void update(ChatSession session) {
        updateById(session);
        CHART_SESSION.invalidate(session.getSessionId());
    }

    @Override
    public void incrementStreamDone(String sessionId) {
        LambdaUpdateWrapper<ChatSession> uw = new LambdaUpdateWrapper<>();
        uw.eq(ChatSession::getSessionId, sessionId)
          .setSql("stream_done = COALESCE(stream_done, 0) + 1");
        update(null, uw);
        CHART_SESSION.invalidate(sessionId);
    }
}
