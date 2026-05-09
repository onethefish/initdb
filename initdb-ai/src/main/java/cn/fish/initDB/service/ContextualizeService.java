package cn.fish.initDB.service;

/**
 * 问句补全：在 {@link cn.fish.initDB.service.impl.DBAgentServiceImpl#chatStream} 中、于工作流图之前同步执行。
 */
public interface ContextualizeService {

    String rewrite(String rawMessage, String sessionId);
}
