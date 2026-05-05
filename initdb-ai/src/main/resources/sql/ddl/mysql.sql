CREATE TABLE agent_datasource
(
    id             VARCHAR(32) NOT NULL PRIMARY KEY COMMENT '数据源ID（主键）',
    name           VARCHAR(255) COMMENT '数据源名称',
    type           VARCHAR(32) COMMENT '数据库类型（如：MySQL、PostgresSQL、Oracle等）',
    host           VARCHAR(16) COMMENT '数据库主机地址',
    port           VARCHAR(32) COMMENT '数据库端口号',
    database_name  VARCHAR(255) COMMENT '数据库名称',
    username       VARCHAR(255) COMMENT '数据库用户名',
    password       VARCHAR(255) COMMENT '数据库密码',
    connection_url VARCHAR(1024) COMMENT '数据库连接URL',
    status         INT COMMENT '数据源状态（如：启用、禁用等）',
    test_status    INT COMMENT '连接测试状态（如：成功、失败等）',
    description    TEXT COMMENT '数据源描述信息'
) COMMENT = 'Agent数据源配置表';

CREATE TABLE chat_session
(
    session_id    VARCHAR(32) NOT NULL PRIMARY KEY COMMENT '会话ID',
    session_name  VARCHAR(255) COMMENT '会话名称',
    datasource_id VARCHAR(32) COMMENT '创建会话时选的数据源ID'
) COMMENT = '聊天会话表';
