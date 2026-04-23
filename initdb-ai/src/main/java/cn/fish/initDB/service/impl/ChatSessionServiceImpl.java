package cn.fish.initDB.service.impl;

import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.repository.ChatSessionRepository;
import cn.fish.initDB.repository.DataBaseRepository;
import cn.fish.initDB.service.ChatSessionService;
import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final DataBaseRepository dataBaseRepository;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository, DataBaseRepository dataBaseRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.dataBaseRepository = dataBaseRepository;
    }

    @Override
    public ChatSession add(ChatSession chatSession) {
        // 测试连接是否可用
        //        dataBaseRepository.test(chatSession);
        // 保存连接信息
        chatSession.setSessionId(IdUtil.simpleUUID());
        try {
            dataBaseRepository.add(chatSession);
        } catch (Exception e) {
            throw new CommonException("数据库连接异常请检查参数", e);
        }
        chatSessionRepository.add(chatSession);
        return chatSession;
    }

    @Override
    public List<ChatSession> queryList(ChatSession chatSession) {
        return chatSessionRepository.queryList(chatSession);
    }

    @Override
    public void delete(ChatSession chatSession) {
        chatSessionRepository.remove(chatSession);
        dataBaseRepository.remove(chatSession);
    }

    @Override
    public void deleteAll() {
        chatSessionRepository.removeAll();
        dataBaseRepository.removeAll();
    }
}
