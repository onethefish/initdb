package cn.fish.initDB.service;

import cn.fish.initDB.entity.ChatSession;

public interface ChatSessionService {

    ChatSession add(ChatSession chatSession);

    void delete(ChatSession chatSession);

    void deleteAll();

}
