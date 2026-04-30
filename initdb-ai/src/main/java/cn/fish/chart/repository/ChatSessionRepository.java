package cn.fish.chart.repository;

import cn.fish.chart.entity.ChatSession;
import com.baomidou.mybatisplus.extension.repository.IRepository;

import java.util.List;

public interface ChatSessionRepository extends IRepository<ChatSession> {

    ChatSession queryUnique(String sessionId);

    List<ChatSession> queryList(ChatSession chatSession);

    void add(ChatSession chatSession);

    void remove(ChatSession chatSession);

    void remove(List<ChatSession> chatSessions);
}
