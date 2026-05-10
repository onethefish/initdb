package cn.fish.chart.entity;

import cn.fish.common.entity.DbBase;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("chat_session")
@EqualsAndHashCode(callSuper = true)
public class ChatSession extends DbBase {

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

    /** 对话流正常结束累计次数（控制自动标题调用频率） */
    private Integer streamDone;

    /** 上次成功自动命名时的 {@link #streamDone} 取值 */
    private Integer namedStream;

}
