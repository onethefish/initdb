package cn.fish.initDB.service;

import cn.fish.initDB.entity.ExportJobCreateRequest;
import cn.fish.initDB.entity.ExportJobDownloadOpen;
import cn.fish.initDB.entity.ExportJobView;
import java.io.IOException;

public interface ExportJobService {

    ExportJobView add(ExportJobCreateRequest request);

    ExportJobView queryUnique(String jobId, String sessionId);
    /**
     * 校验任务并打开 Serva 存储中的导出文件流。
     */
    ExportJobDownloadOpen download(String jobId, String sessionId) throws IOException;
}
