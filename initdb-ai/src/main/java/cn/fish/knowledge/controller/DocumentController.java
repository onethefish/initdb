package cn.fish.knowledge.controller;

import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.knowledge.service.DocumentService;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/document")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 导入txt文档
     *
     * @param file      file
     * @param sessionId sessionId
     * @return ResponseResult
     */
    @PostMapping("/upload/txt")
    public ResponseResult<Void> importTxtDocument(@RequestParam("file") MultipartFile file, @RequestParam("sessionId") String sessionId) {
        documentService.importTxtDocument(sessionId, file);
        return ResponseResult.success();
    }
    /**
     * 查询文档列表
     *
     * @param sessionId sessionId
     * @param query     query
     * @return ResponseResult<List<Document>>
     */
    @GetMapping("/query/list")
    public ResponseResult<List<Document>> queryList(@RequestParam("sessionId") String sessionId, @RequestParam("query") String query) {
        return ResponseResult.success(documentService.queryList(sessionId, query));
    }
}
