package cn.fish.initDB.controller;

import cn.fish.initDB.service.DocumentService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload/txt")
    public String importTxtDocument(@RequestParam("file") MultipartFile file, @RequestParam("sessionId") String sessionId) {
        documentService.importTxtDocument(file, sessionId);
        // 简单处理下 后续换成统一的web对象返回
        return "ok";
    }


}
