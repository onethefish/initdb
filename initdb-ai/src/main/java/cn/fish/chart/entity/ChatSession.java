package cn.fish.chart.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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

    /**
     * 会话ID
     */
    @TableId
    private String sessionId;

    /**
     * 会话名称
     */
    private String sessionName;

    /**
     * 数据库主机地址
     */
    private String host;

    /**
     * 数据库端口
     */
    private String port;

    /**
     * 数据库连接URL
     */
    private String url;

    /**
     * 数据库用户名
     */
    private String username;

    /**
     * 数据库密码
     */
    private String password;

    /**
     * 创建会话时选择的数据源 ID（不入库，仅用于 /chat/create 请求体）
     */
    @TableField(exist = false)
    private String datasourceId;

}
