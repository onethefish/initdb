package cn.fish.chart.repository;

import cn.fish.initDB.entity.ChatSession;

import java.util.List;

public interface ChatSessionRepository {

    ChatSession queryUnique(String sessionId);

    List<ChatSession> queryList(ChatSession chatSession);

    void add(ChatSession chatSession);

    void remove(ChatSession chatSession);

    void removeAll();
}
