package cn.fish.initDB.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("chat_session")
public class ChatSession {

    private String sessionName;

    @TableId
    private String sessionId;
    private String host;
    private String port;
    private String url;
    private String username;
    private String password;

}
