package cn.fish.initDB.service.impl;

import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.repository.ChatSessionRepository;
import cn.fish.initDB.repository.DataBaseRepository;
import cn.fish.initDB.service.ChatSessionService;
import cn.hutool.core.util.IdUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    @Autowired
    private ChatSessionRepository chatSessionRepository;
    @Autowired
    private DataBaseRepository dataBaseRepository;

    @Override
    public ChatSession add(ChatSession chatSession) {
        // 测试连接是否可用
        //        dataBaseRepository.test(chatSession);
        // 保存连接信息
        chatSession.setSessionId(IdUtil.simpleUUID());
        chatSessionRepository.add(chatSession);
        dataBaseRepository.add(chatSession);
        return chatSession;
    }
}
