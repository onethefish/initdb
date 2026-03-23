package cn.fish.initDB.repository;

import cn.fish.initDB.entity.ChatSession;

public interface ChatSessionRepository {

    ChatSession queryUnique(String sessionId);

    void add(ChatSession chatSession);

    void remove(ChatSession chatSession);

    void removeAll();
}
