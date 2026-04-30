package cn.fish.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
@TableName("agent_datasource")
public class AgentDatasource {

    /**
     * 数据源ID（主键）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 数据库类型（如：MySQL、PostgresSQL、Oracle等）
     */
    private String type;

    /**
     * 数据库主机地址
     */
    private String host;

    /**
     * 数据库端口号
     */
    private String port;

    /**
     * 数据库名称
     */
    private String databaseName;

    /**
     * 数据库用户名
     */
    private String username;

    /**
     * 数据库密码
     */
    private String password;

    /**
     * 数据库连接URL
     */
    private String connectionUrl;

    /**
     * 数据源状态（如：启用、禁用等）
     */
    private Integer status;

    /**
     * 连接测试状态（如：成功、失败等）
     */
    private Integer testStatus;

    /**
     * 数据源描述信息
     */
    private String description;
}
