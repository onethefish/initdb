package cn.fish.initDB.repository.impl;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.repository.ChatSessionRepository;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class ChatSessionRepositoryImpl implements ChatSessionRepository {


    private static final Cache<String, ChatSession> CHART_SESSION = Caffeine.newBuilder()
                                                                            .maximumSize(128) // 最大支持128个会话
                                                                            .build();

    @Autowired
    private BaseCheckpointSaver baseCheckpointSaver;

    @Override
    public ChatSession queryUnique(String sessionId) {
        return CHART_SESSION.getIfPresent(sessionId);
    }

    @Override
    public void add(ChatSession chatSession) {
        CHART_SESSION.put(chatSession.getSessionId(), chatSession);
    }

    @Override
    public void remove(ChatSession chatSession) {
        CHART_SESSION.invalidate(chatSession.getSessionId());
        try {
            baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
        } catch (Exception ignored) {

        }
    }

    @Override
    public void removeAll() {
        CHART_SESSION.asMap().forEach((key,value)->{
            try {
                baseCheckpointSaver.release(RunnableConfig.builder().threadId(key).build());
            } catch (Exception ignored) {

            }
        });
        CHART_SESSION.invalidateAll();
    }
}
