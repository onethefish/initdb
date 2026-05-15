package cn.fish.database.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DataSqlValidateRequest {

    @NotBlank
    private String sessionId;

    @NotBlank
    private String sql;
}
