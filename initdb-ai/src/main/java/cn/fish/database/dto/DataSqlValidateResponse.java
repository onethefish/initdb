package cn.fish.database.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataSqlValidateResponse {

    /** 与导出/直连一致：{@code verdict.contains("校验成功")} */
    private boolean ok;

    /** 校验说明（失败时为原因，成功时可为完整 verdict 文案） */
    private String message;
}
