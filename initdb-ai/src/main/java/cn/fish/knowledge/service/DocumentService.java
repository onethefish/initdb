package cn.fish.knowledge.service;

import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {

/**
     * 导入txt文档
     * @param sessionId 会话id
     * @param file 文件
 */
    void importTxtDocument(String sessionId, MultipartFile file);

    /**
     * 查询文档列表
     * @param sessionId sessionId
     * @param query query
     * @return
     */
    List<Document> queryList(String sessionId, String query);
}
