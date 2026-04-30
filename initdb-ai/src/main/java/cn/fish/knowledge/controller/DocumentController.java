package cn.fish.knowledge.controller;

import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.knowledge.service.DocumentService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload/txt")
    public ResponseResult<Void> importTxtDocument(@RequestParam("file") MultipartFile file, @RequestParam("sessionId") String sessionId) {
        documentService.importTxtDocument(sessionId, file);
        return ResponseResult.success();
    }

    @GetMapping("/query/list")
    public ResponseResult<List<Document>> queryList(@RequestParam("sessionId") String sessionId, @RequestParam("query") String query) {
        return ResponseResult.success(documentService.queryList(sessionId, query));
    }
}
