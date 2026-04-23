package cn.fish.initDB.controller;

import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.initDB.service.DocumentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload/txt")
    public ResponseResult<Void> importTxtDocument(@RequestParam("file") MultipartFile file, @RequestParam("sessionId") String sessionId) {
        documentService.importTxtDocument(file, sessionId);
        return ResponseResult.success();
    }


}
