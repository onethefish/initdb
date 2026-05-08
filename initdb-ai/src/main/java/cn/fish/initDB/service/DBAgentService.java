package cn.fish.initDB.service;

import cn.fish.initDB.entity.ChatRequest;
import reactor.core.publisher.Flux;

public interface DBAgentService {

//    ChatResponse chat(ChatRequest chatRequest);

    Flux<String> chatStream(ChatRequest chatRequest);

//    String getDBChart(String message, String sessionId);
}
