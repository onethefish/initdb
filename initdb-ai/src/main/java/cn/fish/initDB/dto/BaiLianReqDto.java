package cn.fish.initDB.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author cgy
 * @since 2024-08-16 18:48
 */
@Getter
@Setter
@ToString
@Accessors(chain = true)
public class BaiLianReqDto {
    /**
     * 提问编号
     */
    private String askCode;

    /**
     * 提问
     */
    private String prompt;

    /**
     * 百炼appCode
     *
     */
    private Integer appCode;

    /**
     * 检索相关的参数
     */
    private List<String> tags;

    /**
     * 上次会话id
     */
    private String lastSessionId;

    public BaiLianReqDto() {
    }

    public BaiLianReqDto(String prompt, Integer appCode) {
        this.prompt = prompt;
        this.appCode = appCode;
    }

}
