package cn.fish.initDB.controller.form;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author xwt
 * @since 2024-08-13 21:14
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AiChatForm {



    /**
     * 问题
     */
    private String prompt;

    /**
     * 上传的会话id
     */
    private String lastSessionId;


}
