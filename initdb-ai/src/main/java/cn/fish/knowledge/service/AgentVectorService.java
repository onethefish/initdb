package cn.fish.knowledge.service;

import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.entity.AgentKnowledgeVO;
import org.springframework.ai.document.Document;

import java.util.List;

public interface AgentVectorService {

    List<Document> queryList(AgentKnowledgeVO vo);

}
