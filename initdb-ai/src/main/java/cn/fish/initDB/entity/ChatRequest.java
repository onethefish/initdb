package cn.fish.initDB.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    private String message;
    private String sessionId;

    /**
     * 可选。若非空白则工作流以该串作为 {@code standalone}；否则使用 {@link #message}（与聊天流中不再做会话补全一致，
     * 补全请走 {@code /db/chat/contextualize} 后由前端填入正式输入框）。
     */
    private String standalone;

}
