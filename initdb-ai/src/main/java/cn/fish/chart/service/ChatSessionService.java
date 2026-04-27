package cn.fish.chart.service;

import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatSession;
import reactor.core.publisher.Flux;

import java.util.List;

public interface ChatSessionService {

    Flux<String> chatStream(ChatRequest chatRequest);

    ChatSession add(ChatSession chatSession);

    List<ChatSession> queryList(ChatSession chatSession);

    void delete(ChatSession chatSession);

    void deleteAll();

}
