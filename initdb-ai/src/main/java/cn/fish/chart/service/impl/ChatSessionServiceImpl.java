package cn.fish.chart.service.impl;

import cn.fish.chart.service.ChatSessionService;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.initDB.entity.ChatRequest;
import cn.fish.initDB.entity.ChatSession;
import cn.fish.initDB.repository.ChatSessionRepository;
import cn.fish.initDB.repository.DataBaseRepository;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
public class ChatSessionServiceImpl implements ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final DataBaseRepository dataBaseRepository;
    private final ChatClient chatClient;
    private final BaseCheckpointSaver baseCheckpointSaver;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository, DataBaseRepository dataBaseRepository, ChatModel chatModel,
                                  BaseCheckpointSaver baseCheckpointSaver) {
        this.chatSessionRepository = chatSessionRepository;
        this.dataBaseRepository = dataBaseRepository;
        this.baseCheckpointSaver = baseCheckpointSaver;
        MessageWindowChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
                                                                                 .maxMessages(10)
                                                                                 .build();
        chatClient = ChatClient.builder(chatModel)
                               .defaultAdvisors(MessageChatMemoryAdvisor.builder(messageWindowChatMemory).build())
                               .build();
    }

    @Override
    public Flux<String> chatStream(ChatRequest chatRequest) {
        String sessionId = chatRequest.getSessionId();
        if (StrUtil.isEmpty(sessionId)) {
            // 纯聊天 不需要id
            //            throw new CommonException("Sorry, an error occurred: sessionId is null");
        }
        ChatClient.ChatClientRequestSpec clientRequestSpec = chatClient.prompt()
                                                                       .user(chatRequest.getMessage())
                                                                       .advisors(memoryAdvisor -> memoryAdvisor
                                                                               .param(ChatMemory.CONVERSATION_ID, sessionId)
                                                                       );
        return clientRequestSpec.stream().content();
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
        try {
            baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
        } catch (Exception ignored) {

        }
    }

    @Override
    public void deleteAll() {
        for (ChatSession chatSession : chatSessionRepository.queryList(null)) {
            try {
                baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
            } catch (Exception ignored) {

            }
        }
        chatSessionRepository.removeAll();
        dataBaseRepository.removeAll();
    }
}
