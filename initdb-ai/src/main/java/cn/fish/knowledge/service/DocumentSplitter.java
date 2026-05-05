package cn.fish.knowledge.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class DocumentSplitter {

    private final EmbeddingModel embeddingModel;
    // 语义相似度阈值（0.0 - 1.0），值越高代表要求语义越接近才不拆分
    private static final double SIMILARITY_THRESHOLD = 0.85;

    public DocumentSplitter(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<Document> semanticSplit(List<Document> documents) {
      List<Document> documentList = new ArrayList<>();
      for (Document document : documents) {
          documentList.addAll(semanticSplit(document));
      }
      return documentList;
    }
    public List<Document> semanticSplit(Document rootDocument) {
        String content = rootDocument.getText();

        // 1. 粗粒度切分：先按句子切分（简单正则实现）
        String[] sentences = content.split("(?<=[。！？；.!?;])");
        if (sentences.length <= 1) return List.of(rootDocument);

        // 2. 获取所有句子的 Embedding 向量
        List<float[]> embeddings = embeddingModel.embed(List.of(sentences));

        List<Document> semanticChunks = new ArrayList<>();
        StringBuilder currentChunkContent = new StringBuilder(sentences[0]);

        // 3. 比较相邻句子的语义距离
        for (int i = 0; i < sentences.length - 1; i++) {
            float[] currentVec = embeddings.get(i);
            float[] nextVec = embeddings.get(i + 1);

            double similarity = cosineSimilarity(currentVec, nextVec);

            if (similarity < SIMILARITY_THRESHOLD) {
                // 如果语义跨度太大，则创建新块
                semanticChunks.add(new Document(currentChunkContent.toString(), rootDocument.getMetadata()));
                currentChunkContent = new StringBuilder(sentences[i + 1]);
            } else {
                // 语义接近，合并到当前块
                currentChunkContent.append(sentences[i + 1]);
            }
        }

        // 添加最后一个块
        if (!currentChunkContent.isEmpty()) {
            semanticChunks.add(new Document(currentChunkContent.toString(), rootDocument.getMetadata()));
        }

        return semanticChunks;
    }

    // 余弦相似度计算公式
    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // 逻辑上包含：Recursive, Paragraph, Sentence
    public List<Document> splitDocument(List<Document> documents) {
        // 参数说明：
        // 1. defaultChunkSize: 每个块的期望大小
        // 2. minChunkSize: 最小块大小
        // 3. keepSeparator: 是否保留分隔符
        // 4. maxNumChunks: 最大块数量
        TextSplitter splitter = new TokenTextSplitter(800, 100, 5, 1000, true);

        return splitter.apply(documents);
    }


    public List<Document> tokenBasedSplit(List<Document> documents) {
        // 设定每个 Chunk 为 500 tokens，重叠部分为 50 tokens
        // 重叠（Overlap）是为了保证语义连贯，防止信息在切分点断开
        TokenTextSplitter splitter = new TokenTextSplitter(500, 50, 5, 1000, true);

        return splitter.split(documents);
    }
}
