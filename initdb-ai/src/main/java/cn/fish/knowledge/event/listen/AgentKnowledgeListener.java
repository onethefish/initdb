package cn.fish.knowledge.event.listen;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.knowledge.converter.DocumentConverter;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.enums.EmbeddingStatus;
import cn.fish.knowledge.enums.KnowledgeType;
import cn.fish.knowledge.event.AgentKnowledgeEmbeddingEvent;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.fish.knowledge.repository.VectorStoreRepository;
import cn.fish.knowledge.splitter.TextSplitterFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
public class AgentKnowledgeListener {

    private final TextSplitterFactory textSplitterFactory;
    private final ServaFile servaFile;
    private final VectorStoreRepository vectorStoreRepository;
    private final AgentKnowledgeRepository agentKnowledgeRepository;

    public AgentKnowledgeListener(VectorStoreRepository vectorStoreRepository, ServaFile servaFile,
                                  AgentKnowledgeRepository agentKnowledgeRepository, TextSplitterFactory textSplitterFactory) {
        this.vectorStoreRepository = vectorStoreRepository;
        this.servaFile = servaFile;
        this.agentKnowledgeRepository = agentKnowledgeRepository;
        this.textSplitterFactory = textSplitterFactory;
    }


    @Async("initDbExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void agentKnowledgeEvent(AgentKnowledgeEmbeddingEvent event) {
        AgentKnowledge agentKnowledge = event.getAgentKnowledge();
        String id = agentKnowledge.getId();
        log.info("Received AgentKnowledgeEmbeddingEvent. agentKnowledgeId: {}", id);
        // 虽然传了整个对象但是其实是异步的 可能被其它线程抢状态
        AgentKnowledge current = agentKnowledgeRepository.getById(id);
        try {
            updateStatus(current, EmbeddingStatus.PROCESSING, null);
            // 不同的类型有不同得到处理方法
            String type = current.getType();
            // 问答、常见类型
            if (KnowledgeType.QA.getCode().equals(type) || KnowledgeType.FAQ.getCode().equals(type)) {
                // todo 增加提示词 转换为文档

            }
            // 文件
            else if (KnowledgeType.DOCUMENT.getCode().equals(type)) {
                Resource resource = servaFile.getFileResource(current.getFileId());
                // 使用TikaDocumentReader读取文件
                TikaDocumentReader tikaDocumentReader = new TikaDocumentReader(resource);
                List<Document> documents = tikaDocumentReader.read();
                TextSplitter splitter = textSplitterFactory.getSplitter(current.getSplitterType());
                List<Document> result = DocumentConverter.convertAgentKnowledgeDocumentsWithMetadata(splitter.split(documents), current);
                vectorStoreRepository.add(result);
            }
            updateStatus(current, EmbeddingStatus.COMPLETED, null);

        } catch (RuntimeException e) {
            updateStatus(current, EmbeddingStatus.FAILED, e.getMessage());
        }
    }


    private void updateStatus(AgentKnowledge knowledge, EmbeddingStatus status, String errorMsg) {
        knowledge.setEmbeddingStatus(status.getValue());
        if (errorMsg != null) {
            // 截断错误信息防止数据库报错
            knowledge.setErrorMsg(errorMsg.length() > 1024 ? errorMsg.substring(0, 1024) : errorMsg);
        }
        agentKnowledgeRepository.updateById(knowledge);
    }


    private static void addMetadata(Long timestamp, List<Document> splitDocuments) {
        for (Document document : splitDocuments) {
            document.getMetadata().put("split_date", timestamp);
        }
    }


}
