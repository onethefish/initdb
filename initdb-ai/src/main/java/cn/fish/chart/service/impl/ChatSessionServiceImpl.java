package cn.fish.chart.service.impl;

import cn.fish.chart.entity.ChatSession;
import cn.fish.chart.repository.ChatSessionRepository;
import cn.fish.chart.service.ChatSessionService;
import cn.fish.cloud.serva.web.exception.CommonException;
import cn.fish.database.repository.DataBaseRepository;
import cn.fish.datasource.entity.AgentDatasource;
import cn.fish.datasource.repository.AgentDatasourceRepository;
import cn.fish.initDB.entity.ChatRequest;
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
    private final AgentDatasourceRepository agentDatasourceRepository;
    private final ChatClient chatClient;
    private final BaseCheckpointSaver baseCheckpointSaver;

    public ChatSessionServiceImpl(ChatSessionRepository chatSessionRepository, DataBaseRepository dataBaseRepository,
                                  AgentDatasourceRepository agentDatasourceRepository, ChatModel chatModel,
                                  BaseCheckpointSaver baseCheckpointSaver) {
        this.chatSessionRepository = chatSessionRepository;
        this.dataBaseRepository = dataBaseRepository;
        this.agentDatasourceRepository = agentDatasourceRepository;
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
        chatSession.setSessionId(IdUtil.simpleUUID());
        validateDataSource(chatSession);
        chatSessionRepository.add(chatSession);
        return chatSession;
    }

    private void validateDataSource(ChatSession chatSession) {
        if (StrUtil.isBlank(chatSession.getDatasourceId())) {
            throw new CommonException("请选择数据源");
        }
        AgentDatasource ds = agentDatasourceRepository.getById(chatSession.getDatasourceId());
        if (ds == null) {
            throw new CommonException("数据源不存在");
        }
        if (!Integer.valueOf(1).equals(ds.getStatus()) || !Integer.valueOf(1).equals(ds.getTestStatus())) {
            throw new CommonException("仅可选择已启用且连接测试成功的数据源，请先在数据源管理中配置并测试");
        }
        if (StrUtil.isBlank(ds.getConnectionUrl()) || StrUtil.isBlank(ds.getUsername())) {
            throw new CommonException("数据源连接信息不完整");
        }
        try {
            dataBaseRepository.add(chatSession.getSessionId(), ds.getConnectionUrl(), ds.getUsername(), ds.getPassword());
        } catch (Exception e) {
            throw new CommonException("数据库连接异常请检查参数", e);
        }

    }

    @Override
    public List<ChatSession> queryList(ChatSession chatSession) {
        return chatSessionRepository.queryList(chatSession);
    }

    @Override
    public void delete(ChatSession chatSession) {
        chatSessionRepository.remove(chatSession);
        dataBaseRepository.remove(chatSession.getSessionId());
        try {
            baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
        } catch (Exception ignored) {

        }
    }

    @Override
    public void deleteAll() {
        List<ChatSession> chatSessions = chatSessionRepository.queryList(new ChatSession());
        try {
            for (ChatSession chatSession : chatSessions) {
                baseCheckpointSaver.release(RunnableConfig.builder().threadId(chatSession.getSessionId()).build());
            }
        } catch (Exception ignored) {

        }
        chatSessionRepository.remove(chatSessions);
        dataBaseRepository.removeAll();
    }
}
