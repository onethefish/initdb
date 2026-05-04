package cn.fish.chart.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
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
    @TableField("session_name")
    private String sessionName;
    /**
     * 数据库类型（如：mysql、postgresql）
     */
    @TableField("type")
    private String type;
    /**
     * 数据库主机地址
     */
    @TableField("host")
    private String host;

    /**
     * 数据库端口
     */
    @TableField("port")
    private String port;

    /**
     * 数据库连接URL
     */
    @TableField("url")
    private String url;

    /**
     * 数据库用户名
     */
    @TableField("username")
    private String username;

    /**
     * 数据库密码
     */
    @TableField("password")
    private String password;
    /**
     * 数据库名称
     */
    @TableField("database_name")
    private String databaseName;
    /**
     * schema
     */
    @TableField("schema_name")
    private String schemaName;
    /**
     * 创建会话时选择的数据源 ID（不入库，仅用于 /chat/create 请求体）
     */
    @TableField(exist = false)
    private String datasourceId;


}
