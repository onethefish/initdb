package cn.fish.initDB.config;

import cn.fish.initDB.savers.ChatMemorySaver;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RepositoryConfig {

    // 提供一个RAG向量库 todo
    @Bean
    public VectorStore createVectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ChatMemorySaver createChatMemorySaver() {
        return new ChatMemorySaver();
    }
}
