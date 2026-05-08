package cn.fish.common.config;

import cn.fish.common.savers.ChatMemorySaver;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class RepositoryConfig {

    // 提供一个RAG向量库（pg）
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        PgVectorStore.PgVectorStoreBuilder builder = PgVectorStore.builder(jdbcTemplate, embeddingModel);
        //        builder.initializeSchema(true);
        return builder.build();
    }

    // 提供一个RAG向量库（内存） 后续调整为配置文件注入
    //    @Bean
    //    public VectorStore createVectorStore(EmbeddingModel embeddingModel) {
    //        return SimpleVectorStore.builder(embeddingModel).build();
    //    }

    // 会话记忆
    @Bean
    public ChatMemorySaver createChatMemorySaver() {
        return new ChatMemorySaver();
    }
}
