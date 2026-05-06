package cn.fish.config;

import cn.fish.common.properties.TextSplitterProperties;
import cn.fish.knowledge.splitter.ParagraphTextSplitter;
import cn.fish.knowledge.splitter.SemanticTextSplitter;
import cn.fish.knowledge.splitter.SentenceSplitter;
import com.alibaba.cloud.ai.transformer.splitter.RecursiveCharacterTextSplitter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class TextSplitterConfig {


    @Bean(name = "token")
    public TextSplitter textSplitter(TextSplitterProperties textSplitterProps) {
        TextSplitterProperties.TokenTextSplitterConfig config = textSplitterProps.getToken();
        return new TokenTextSplitter(textSplitterProps.getChunkSize(), config.getMinChunkSizeChars(),
                config.getMinChunkLengthToEmbed(), config.getMaxNumChunks(), config.isKeepSeparator());
    }

    /**
     * 递归字符文本分块器
     * @param textSplitterProps 分块配置
     * @return RecursiveCharacterTextSplitter实例
     */
    @Bean(name = "recursive")
    public TextSplitter recursiveTextSplitter(TextSplitterProperties textSplitterProps) {
        TextSplitterProperties.RecursiveTextSplitterConfig config = textSplitterProps.getRecursive();
        // RecursiveCharacterTextSplitter
        String[] separators = config.getSeparators();
        if (separators != null && separators.length > 0) {
            return new RecursiveCharacterTextSplitter(textSplitterProps.getChunkSize(), separators);
        }
        else {
            return new RecursiveCharacterTextSplitter(textSplitterProps.getChunkSize());
        }
    }

    /**
     * 句子分块器
     * @param textSplitterProps 分块配置
     * @return SentenceSplitter实例
     */
    @Bean(name = "sentence")
    public TextSplitter sentenceSplitter(TextSplitterProperties textSplitterProps) {
        TextSplitterProperties.SentenceTextSplitterConfig sentenceConfig = textSplitterProps.getSentence();
        return SentenceSplitter.builder()
                               .withChunkSize(textSplitterProps.getChunkSize())
                               .withSentenceOverlap(sentenceConfig.getSentenceOverlap())
                               .build();
    }

    /**
     * 语义分块器
     * @param textSplitterProps 分块配置
     * @param embeddingModel Embedding 模型
     * @return SemanticTextSplitter实例
     */
    @Bean(name = "semantic")
    public TextSplitter semanticSplitter(TextSplitterProperties textSplitterProps, EmbeddingModel embeddingModel) {
        TextSplitterProperties.SemanticTextSplitterConfig config = textSplitterProps.getSemantic();
        return SemanticTextSplitter.builder()
                                   .embeddingModel(embeddingModel)
                                   .minChunkSize(config.getMinChunkSize())
                                   .maxChunkSize(config.getMaxChunkSize())
                                   .similarityThreshold(config.getSimilarityThreshold())
                                   .build();
    }

    /**
     * 段落分块器
     * @param textSplitterProps 分块配置
     * @return ParagraphTextSplitter实例
     */
    @Bean(name = "paragraph")
    public TextSplitter paragraphSplitter(TextSplitterProperties textSplitterProps) {
        TextSplitterProperties.ParagraphTextSplitterConfig config = textSplitterProps.getParagraph();
        return ParagraphTextSplitter.builder()
                                    .chunkSize(textSplitterProps.getChunkSize())
                                    .paragraphOverlapChars(config.getParagraphOverlapChars())
                                    .build();
    }


}
