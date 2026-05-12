package cn.fish.initDB.controller;

import cn.fish.cloud.serva.web.response.ResponseResult;
import cn.fish.cloud.serva.web.utils.ResponseDownloadUtil;
import cn.fish.initDB.entity.ExportJobCreateRequest;
import cn.fish.initDB.entity.ExportJobDownloadOpen;
import cn.fish.initDB.entity.ExportJobView;
import cn.fish.initDB.enums.ExportFormat;
import cn.fish.initDB.service.ExportJobService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * 异步导出：创建任务、查询状态、下载产物（与 {@code /db/chat/stream} 独立）。
 */
@RestController
@RequestMapping("/db/export/jobs")
public class ExportJobController {

    private final ExportJobService exportJobService;

    public ExportJobController(ExportJobService exportJobService) {
        this.exportJobService = exportJobService;
    }

    @PostMapping("/add")
    public ResponseResult<ExportJobView> add(@Valid @RequestBody ExportJobCreateRequest request) {
        return ResponseResult.success(exportJobService.add(request));
    }

    @GetMapping("/query/unique")
    public ResponseResult<ExportJobView> queryUnique(@RequestParam("id") String id, @RequestParam("sessionId") String sessionId) {
        return ResponseResult.success(exportJobService.queryUnique(id, sessionId));
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(
            @RequestParam("id") String id,
            @RequestParam("sessionId") String sessionId) throws IOException {
        ExportJobDownloadOpen opened = exportJobService.download(id, sessionId);
        ExportFormat format = opened.format();
        String filename = "export-" + id + format.fileSuffix();
        return ResponseDownloadUtil.download(opened.inputStream(), filename, format.mediaType());
    }
}
