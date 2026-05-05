package cn.fish.initDB.event.listen;

import cn.fish.cloud.serva.file.operate.ServaFile;
import cn.fish.initDB.event.AgentKnowledgeAddEvent;
import cn.fish.knowledge.entity.AgentKnowledge;
import cn.fish.knowledge.repository.AgentKnowledgeRepository;
import cn.fish.knowledge.repository.VectorStoreRepository;
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
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentKnowledgeListener {
    private final VectorStoreRepository vectorStoreRepository;
    private final ServaFile servaFile;
    private final AgentKnowledgeRepository agentKnowledgeRepository;


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
            // 1. 将 java.io.File 包装为 Spring Resource
            FileSystemResource fileResource = new FileSystemResource(file);

            // 2. 使用 Tika 文档读取器（自动识别所有文件格式）
            TikaDocumentReader documentReader = new TikaDocumentReader(fileResource);

            // 3. 读取并返回 Document 集合
            List<Document> splitDocuments = documentReader.read();
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
}
