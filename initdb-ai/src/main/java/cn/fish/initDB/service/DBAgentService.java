package cn.fish.initDB.service;

import cn.fish.initDB.bo.AiChatAskBo;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatResponse;
import reactor.core.publisher.Flux;

import java.io.OutputStream;

public interface DBAgentService {

    ChatResponse chat(ChatRequest chatRequest);

    Flux<String> chatStream(ChatRequest chatRequest);

    /**
     * 提问
     * @param   os os
     * @param bo bo
     */
    void ask(OutputStream os, AiChatAskBo bo);
}
