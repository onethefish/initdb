package cn.fish.knowledge.event.listen;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.knowledge.constants.DocumentMetadataConstant;
import cn.fish.knowledge.converter.DocumentConverter;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.enums.EmbeddingStatus;
import cn.fish.knowledge.enums.KnowledgeType;
import cn.fish.knowledge.event.AgentKnowledgeDeleteEvent;
import cn.fish.knowledge.event.AgentKnowledgeEmbeddingEvent;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.fish.knowledge.repository.VectorStoreRepository;
import cn.fish.knowledge.splitter.TextSplitterFactory;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public void agentKnowledgeEmbeddingEvent(AgentKnowledgeEmbeddingEvent event) {
        AgentKnowledge agentKnowledge = event.getAgentKnowledge();
        String id = agentKnowledge.getId();
        log.info("Received AgentKnowledgeEmbeddingEvent. agentKnowledgeId: {}", id);
        // 虽然传了整个对象但是其实是异步的 可能被其它线程抢状态
        AgentKnowledge current = agentKnowledgeRepository.getById(id);
        // 没有就啥都不干
        if (ObjectUtil.isEmpty(current)) {
            return;
        }
        try {
            updateStatus(current, EmbeddingStatus.PROCESSING, null);

            deleteVectorStore(current);

            addVectorStore(current);
            updateStatus(current, EmbeddingStatus.COMPLETED, null);
        } catch (RuntimeException e) {
            log.error(e.getMessage(), e);
            updateStatus(current, EmbeddingStatus.FAILED, e.getMessage());
        }
    }


    @Async("initDbExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void agentKnowledgeDeleteEvent(AgentKnowledgeDeleteEvent event) {
        AgentKnowledge agentKnowledge = event.getAgentKnowledge();
        String id = agentKnowledge.getId();
        log.info("Received agentKnowledgeDeleteEvent. agentKnowledgeId: {}", id);
        deleteVectorStore(agentKnowledge);
    }

    private void addVectorStore(AgentKnowledge current) {
        // 不同的类型有不同得到处理方法
        String type = current.getType();
        // 问答、常见类型
        if (KnowledgeType.QA.getCode().equals(type) || KnowledgeType.FAQ.getCode().equals(type)) {
            //  增加提示词 转换为文档
            String content = current.getContent();
            String question = current.getQuestion();
            String chatContent = StrUtil.format("问题：{}\n,回答:{} \n", question, content);
            Document document = new Document(chatContent, Map.of("question", current.getQuestion()));
            // 不用分割 单个内容非常少
            List<Document> result = DocumentConverter.convertAgentKnowledgeDocumentsWithMetadata(CollUtil.newArrayList(document), current);
            vectorStoreRepository.add(result);
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
    }

    private void deleteVectorStore(AgentKnowledge current) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        ArrayList<FilterExpressionBuilder.Op> ops = new ArrayList<>();
        if (StrUtil.isNotBlank(current.getDatasourceId())) {
            ops.add(b.eq(DocumentMetadataConstant.DATASOURCE_ID, current.getDatasourceId()));
        }
        ops.add(b.eq(DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID, current.getId()));
        ops.add(b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE));
        FilterExpressionBuilder.Op combined = ops.get(0);
        for (int i = 1; i < ops.size(); i++) {
            combined = b.and(combined, ops.get(i));
        }
        vectorStoreRepository.delete(combined.build());
        log.info("Deleted vector chunks for agentKnowledgeId: {}", current.getId());
    }

    private void updateStatus(AgentKnowledge knowledge, EmbeddingStatus status, String errorMsg) {
        knowledge.setEmbeddingStatus(status.getCode());
        if (errorMsg != null) {
            // 截断错误信息防止数据库报错
            knowledge.setErrorMsg(errorMsg.length() > 1024 ? errorMsg.substring(0, 1024) : errorMsg);
        }
        agentKnowledgeRepository.updateById(knowledge);
    }


}
