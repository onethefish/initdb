package cn.fish.initDB.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CaptchaLoginRequest {

    @NotBlank(message = "手机号或邮箱不能为空")
    private String target;

    @NotBlank(message = "验证码不能为空")
    private String captcha;

    private String type = "PHONE";
}
