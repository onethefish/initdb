package cn.fish.initDB.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    private String sessionName;
    private String sessionId;
    private String host;
    private String port;
    private String url;
    private String username;
    private String password;

}
