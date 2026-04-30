package cn.fish.knowledge.service;

import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {


    void importTxtDocument(String sessionId, MultipartFile file);

    List<Document> queryList(String sessionId, String query);
}
