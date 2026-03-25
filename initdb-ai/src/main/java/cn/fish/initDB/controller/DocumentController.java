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
    public void importTxtDocument(MultipartFile file, @RequestParam String sessionId) {
        documentService.importTxtDocument(file, sessionId);
    }


}
