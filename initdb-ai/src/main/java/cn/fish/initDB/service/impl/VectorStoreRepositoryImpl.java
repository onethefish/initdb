package cn.fish.initDB.service.impl;

import cn.fish.initDB.repository.VectorStoreRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class VectorStoreRepositoryImpl implements VectorStoreRepository {

    private final VectorStore vectorStore;

    public VectorStoreRepositoryImpl(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void add(List<Document> documents, String sessionId) {
        // todo 绑定会话文档防止串读
        vectorStore.add(documents);
    }



}
