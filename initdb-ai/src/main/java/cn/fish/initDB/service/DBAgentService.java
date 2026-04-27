package cn.fish.initDB.service;

import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;

public interface DBAgentService {

    ChatResponse chat(ChatRequest chatRequest);

    String getDBChart(String message, String sessionId);
}
