package cn.fish.initDB.entity;

import cn.fish.common.entity.DbBase;
import cn.fish.initDB.enums.ExportFormat;
import cn.fish.initDB.enums.ExportJobStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("export_job")
@EqualsAndHashCode(callSuper = true)
public class ExportJob extends DbBase {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String sessionId;

    /** {@link ExportFormat#name()} */
    private String format;

    private Integer maxRows;

    private String submittedSql;

    private String executedSql;

    /** {@link ExportJobStatus#name()} */
    private String status;

    private String servaFileId;

    private Long rowCount;

    private String errorMessage;

    private LocalDateTime createdTime;

    private LocalDateTime expiresAt;

    private LocalDateTime finishedAt;
}
