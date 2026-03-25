package cn.fish.initDB.service;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {


    void importTxtDocument(MultipartFile file, @RequestParam String sessionId);

}
