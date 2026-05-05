package cn.fish.initDB.event.listen;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.initDB.event.AgentKnowledgeAddEvent;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.fish.knowledge.repository.VectorStoreRepository;
import cn.fish.knowledge.service.DocumentSplitter;
import cn.hutool.core.util.ObjUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentKnowledgeListener {
    private final VectorStoreRepository vectorStoreRepository;
    private final ServaFile servaFile;
    private final AgentKnowledgeRepository agentKnowledgeRepository;
    private final DocumentSplitter documentSplitter;


    @Async
    @EventListener
    public void agentKnowledgeEvent(AgentKnowledgeAddEvent event) {
        AgentKnowledge knowledge = agentKnowledgeRepository.findById(event.getKnowledgeId());
        if (ObjUtil.equals(knowledge.getKnowledgeInfo().getIsRecall(), 0)) {
            return;
        }
        knowledge.uploading();
        try {
            File file = servaFile.getFile(knowledge.getKnowledgeInfo().getFileId());
            List<Document> splitDocuments = splitDocumentByType(file, knowledge.getKnowledgeInfo().getSplitterType());
            addMetadata(LocalDate.now(), splitDocuments);
            vectorStoreRepository.add(splitDocuments);
            knowledge.complete();

        } catch (Exception e) {
            log.error("上传向量失败: {}", e.getMessage(), e);
            knowledge.fail(e.getMessage());
        } finally {
            // 更新状态
            agentKnowledgeRepository.updateKnowledge(knowledge);
        }
    }

    private List<Document> splitDocumentByType(File file, String splitterType) {
        // 1. 将 java.io.File 包装为 Spring Resource
        FileSystemResource fileResource = new FileSystemResource(file);

        // 2. 使用 Tika 文档读取器（自动识别所有文件格式）
        TikaDocumentReader documentReader = new TikaDocumentReader(fileResource);
        List<Document> documents = documentReader.read();
        // 	TOKEN("token"), RECURSIVE("recursive"), SENTENCE("sentence"), PARAGRAPH("paragraph"), SEMANTIC("semantic");
        switch (splitterType) {
            case "token":
                return documentSplitter.tokenBasedSplit(documents);
            case "recursive":
                return documentSplitter.tokenBasedSplit(documents);
            case "semantic":
                return documentSplitter.semanticSplit(documents);
            default:
                return documentSplitter.tokenBasedSplit(documents);
        }
    }

    private static void addMetadata(LocalDate sessionId, List<Document> splitDocuments) {
        for (Document document : splitDocuments) {
            document.getMetadata().put("split_date", sessionId);
        }
    }


}
