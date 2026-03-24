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
public class BaiLianRespDto {

    /**
     * 问答编号
     */
    private String askCode;

    /**
     * 会话id
     */
    private String sessionId;

    /**
     * 返回
     */
    private String reply;


    /**
     * 公司名称
     */
    private List<String> companyNames;

}
