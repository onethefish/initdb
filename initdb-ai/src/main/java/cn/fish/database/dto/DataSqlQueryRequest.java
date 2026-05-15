package cn.fish.database.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataSqlQueryRequest {

    @NotBlank
    private String sessionId;

    @NotBlank
    private String sql;

    @Min(1)
    @Max(100_000)
    private Integer pageNum = 1;

    @Min(1)
    @Max(500)
    private Integer pageSize = 20;
}
