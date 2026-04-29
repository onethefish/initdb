package cn.fish.initDB.repository.impl;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.mapper.ChatSessionMapper;
import cn.fish.initDB.repository.ChatSessionRepository;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;

@Slf4j
@Repository
public class ChatSessionRepositoryImpl implements ChatSessionRepository {

    private final ChatSessionMapper chatSessionMapper;
    private final BaseCheckpointSaver baseCheckpointSaver;

    public ChatSessionRepositoryImpl(ChatSessionMapper chatSessionMapper, BaseCheckpointSaver baseCheckpointSaver) {
        this.chatSessionMapper = chatSessionMapper;
        this.baseCheckpointSaver = baseCheckpointSaver;
    }

    @Override
    public ChatSession queryUnique(String sessionId) {
        return chatSessionMapper.selectById(sessionId);
    }

    @Override
    public List<ChatSession> queryList(ChatSession chatSession) {
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(ChatSession::getSessionId);
        return chatSessionMapper.selectList(queryWrapper);
    }

    @Override
    public void add(ChatSession chatSession) {
        chatSessionMapper.insertOrUpdate(chatSession);
    }

    @Override
    public void remove(ChatSession chatSession) {
        chatSessionMapper.deleteById(chatSession.getSessionId());
        try {
            baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
        } catch (Exception e) {
            log.debug("Failed to release checkpoint for session: {}", chatSession.getSessionId(), e);
        }
    }

    @Override
    public void removeAll() {
        List<ChatSession> sessions = queryList(null);
        chatSessionMapper.delete(new LambdaQueryWrapper<>());
        for (ChatSession session : sessions) {
            try {
                baseCheckpointSaver.release(RunnableConfig.builder().threadId(session.getSessionId()).build());
            } catch (Exception e) {
                log.debug("Failed to release checkpoint for session: {}", session.getSessionId(), e);
            }
        }
    }
}