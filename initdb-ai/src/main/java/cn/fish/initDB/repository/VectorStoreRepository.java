package cn.fish.initDB.repository;

import org.springframework.ai.document.Document;

import java.util.List;

public interface VectorStoreRepository {

    void add(List<Document> documents, String sessionId);

}
