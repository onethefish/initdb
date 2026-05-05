package cn.fish.chart.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
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
    private String sessionName;

    /**
     * 创建会话时选择的数据源 ID
     */
    private String datasourceId;


}
