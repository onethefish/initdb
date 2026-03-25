package cn.fish.initDB.service;

import cn.fish.initDB.entity.ChatSession;

import java.util.List;

public interface ChatSessionService {

    ChatSession add(ChatSession chatSession);

    List<ChatSession> queryList(ChatSession chatSession);

    void delete(ChatSession chatSession);

    void deleteAll();

}
