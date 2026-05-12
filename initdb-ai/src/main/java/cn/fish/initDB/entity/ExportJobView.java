package cn.fish.initDB.entity;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ExportJobView {
    String id;
    String sessionId;
    String format;
    Integer maxRows;
    String status;
    Long rowCount;
    String errorMessage;
    LocalDateTime createdTime;
    LocalDateTime expiresAt;
    LocalDateTime finishedAt;
    boolean downloadReady;
}
