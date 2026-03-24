package cn.fish.initDB.bo;


import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author cgy
 * @since 2024-12-25 12:30
 */
@Getter
@Setter
@Accessors(chain = true)
public class AiChatAskBo {



    /**
     * 会话编号
     */
    private String chatCode;

    /**
     * 提问内容
     */
    private String prompt;


    /**
     * 上次会话id
     */
    private String lastSessionId;

    /**
     * 检索相关的参数
     */
    private List<String> tags;


}
