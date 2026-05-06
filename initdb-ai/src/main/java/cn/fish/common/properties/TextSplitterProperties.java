package cn.fish.common.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "spring.ai.init-db.text-splitter")
public class TextSplitterProperties {

    /**
     * 默认分块大小，基于token数量 默认值：1000
     */
    private int chunkSize = 1000;

    /**
     * TokenTextSplitter 策略配置
     */
    private TokenTextSplitterConfig token = new TokenTextSplitterConfig();

    /**
     * RecursiveCharacterTextSplitter 策略配置
     */
    private RecursiveTextSplitterConfig recursive = new RecursiveTextSplitterConfig();

    /**
     * SentenceTextSplitter 策略配置
     */
    private SentenceTextSplitterConfig sentence = new SentenceTextSplitterConfig();

    /**
     * SemanticTextSplitter 策略配置
     */
    private SemanticTextSplitterConfig semantic = new SemanticTextSplitterConfig();

    /**
     * ParagraphTextSplitter 策略配置
     */
    private ParagraphTextSplitterConfig paragraph = new ParagraphTextSplitterConfig();

    /**
     * TokenTextSplitter 策略配置
     */
    @Getter
    @Setter
    public static class TokenTextSplitterConfig {

        /**
         * 最小分块字符数 默认值：400
         */
        private int minChunkSizeChars = 400;

        /**
         * 嵌入最小分块长度 默认值：10
         */
        private int minChunkLengthToEmbed = 10;

        /**
         * 最大分块数量 默认值：5000
         */
        private int maxNumChunks = 5000;

        /**
         * 是否保留分隔符 默认值：true
         */
        private boolean keepSeparator = true;

    }

    /**
     * RecursiveCharacterTextSplitter 策略配置
     */
    @Getter
    @Setter
    public static class RecursiveTextSplitterConfig {

        /**
         * 重叠区域字符数 默认值：200
         */
        private int chunkOverlap = 200;

        /**
         * 分隔符列表（如果为 null，该类内部有默认的分隔符列表）
         */
        private String[] separators = null;

    }

    /**
     * SentenceTextSplitter 策略配置
     */
    @Getter
    @Setter
    public static class SentenceTextSplitterConfig {

        /**
         * 句子重叠数量 默认值：1（保留前一个分块的最后1个句子）
         */
        private int sentenceOverlap = 1;

    }

    /**
     * SemanticTextSplitter 策略配置
     */
    @Getter
    @Setter
    public static class SemanticTextSplitterConfig {

        /**
         * 最小分块大小 默认值：200
         */
        private int minChunkSize = 200;

        /**
         * 最大分块大小 默认值：1000
         */
        private int maxChunkSize = 1000;

        /**
         * 语义相似度阈值 默认值：0.5（0-1之间，越低越容易分块）
         */
        private double similarityThreshold = 0.5;

    }

    /**
     * ParagraphTextSplitter 策略配置
     */
    @Getter
    @Setter
    public static class ParagraphTextSplitterConfig {

        /**
         * 段落重叠字符数 默认值：200（保留前一个分块的最后200个字符，而非段落数量）
         */
        private int paragraphOverlapChars = 200;

    }

}
