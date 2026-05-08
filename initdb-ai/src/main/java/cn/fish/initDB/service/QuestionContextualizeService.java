package cn.fish.initDB.service;

import cn.fish.initDB.entity.QuestionContextualizeResult;

/**
 * 问句补全：在 {@link cn.fish.initDB.service.impl.DBAgentServiceImpl#chatStream} 中、于工作流图之前同步执行。
 */
public interface QuestionContextualizeService {

    QuestionContextualizeResult rewrite(String rawMessage, String sessionId);
}
