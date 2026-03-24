package cn.fish.web.response;

import lombok.Getter;

/**
 * 前后端状态枚举类
 *
 * @author onethefish
 */
@Getter
public enum StatusEnum {
    /** 成功 */
    SUCCESS("200", "成功"),
    /** 失败 */
    FAIL("300", "失败"),
    /** 互踢 */
    OFFLINE_USER("301", "互踢"),
    /** JWT过期 */
    JWT_EXPIRE("302", "JWT过期"),
    /** JWT异常 */
    JWT_ERROR("303", "JWT异常"),
    /** 登录验证码错误 */
    VERIFY_CODE_ERROR("304", "登录验证码错误");

    private final String code;
    private final String description;

    public boolean is(String code) {
        return this.code.equals(code);
    }

    StatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

}
