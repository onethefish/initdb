package cn.fish.knowledge.repository;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;

public interface VectorStoreRepository {

    void add(List<Document> documents);

    List<Document> queryList(SearchRequest searchRequest);

    void delete(Filter.Expression filterExpression);
}
