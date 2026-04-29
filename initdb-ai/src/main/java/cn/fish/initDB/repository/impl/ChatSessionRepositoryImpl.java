package cn.fish.initDB.repository.impl;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.repository.ChatSessionRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ChatSessionRepositoryImpl implements ChatSessionRepository {


    private static final Cache<String, ChatSession> CHART_SESSION = Caffeine.newBuilder()
                                                                            .maximumSize(128) // 最大支持128个会话
                                                                            .build();

    @Override
    public ChatSession queryUnique(String sessionId) {
        return CHART_SESSION.getIfPresent(sessionId);
    }

    @Override
    public List<ChatSession> queryList(ChatSession chatSession) {
        // todo
        return new ArrayList<>(CHART_SESSION.asMap().values());
    }

    @Override
    public void add(ChatSession chatSession) {
        CHART_SESSION.put(chatSession.getSessionId(), chatSession);
    }

    @Override
    public void remove(ChatSession chatSession) {
        CHART_SESSION.invalidate(chatSession.getSessionId());
    }

    @Override
    public void removeAll() {
        CHART_SESSION.invalidateAll();
    }
}
