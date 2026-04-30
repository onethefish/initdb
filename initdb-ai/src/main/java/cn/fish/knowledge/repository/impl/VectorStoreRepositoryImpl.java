package cn.fish.knowledge.repository.impl;

import cn.fish.knowledge.repository.VectorStoreRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
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
    public void add(List<Document> documents) {
        vectorStore.add(documents);
    }

    @Override
    public List<Document> queryList(SearchRequest searchRequest) {
        return vectorStore.similaritySearch(searchRequest);
    }


}
