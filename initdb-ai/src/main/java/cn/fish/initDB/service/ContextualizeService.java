package cn.fish.initDB.service;

/**
 * 问句补全：仅由 {@code POST /db/chat/contextualize} 显式调用；聊天流 {@link cn.fish.initDB.service.impl.DBAgentServiceImpl#chatStream} 不再触发补全。
 */
public interface ContextualizeService {

    String rewrite(String rawMessage, String sessionId);
}
