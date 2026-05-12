package cn.fish.initDB.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExportJobCreateRequest {

    @NotBlank
    private String sessionId;

    @NotBlank
    private String sql;

    /** 允许 {@code CSV}、{@code XLSX}、{@code EXCEL} */
    @NotBlank
    private String format;

    /**
     * 期望导出的最大行数；服务端会再与 {@code initdb.export.max-rows} 取较小值。
     * 不传时由服务端使用配置默认上限。
     */
    @Min(1)
    @Max(Integer.MAX_VALUE)
    private Integer maxRows;
}
