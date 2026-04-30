CREATE TABLE chat_session
(
    session_id   VARCHAR(32) PRIMARY KEY, -- 会话ID
    session_name VARCHAR(255),             -- 会话名称
    host         VARCHAR(16),             -- 数据库主机地址
    port         VARCHAR(8),              -- 数据库端口
    url          VARCHAR(1024),             -- 数据库连接URL
    username     VARCHAR(255),             -- 数据库用户名
    password     VARCHAR(255)              -- 数据库密码
);

COMMENT ON TABLE chat_session IS '聊天会话表';
COMMENT ON COLUMN chat_session.session_id IS '会话ID';
COMMENT ON COLUMN chat_session.session_name IS '会话名称';
COMMENT ON COLUMN chat_session.host IS '数据库主机地址';
COMMENT ON COLUMN chat_session.port IS '数据库端口';
COMMENT ON COLUMN chat_session.url IS '数据库连接URL';
COMMENT ON COLUMN chat_session.username IS '数据库用户名';
COMMENT ON COLUMN chat_session.password IS '数据库密码';


CREATE TABLE agent_datasource
(
    id            VARCHAR(32) PRIMARY KEY, -- 数据源ID（主键）
    name          VARCHAR(255),             -- 数据源名称
    type          VARCHAR(8),              -- 数据库类型（如：MySQL、PostgresSQL、Oracle等）
    host          VARCHAR(16),             -- 数据库主机地址
    port          VARCHAR(8),                  -- 数据库端口号
    database_name VARCHAR(255),             -- 数据库名称
    username      VARCHAR(255),             -- 数据库用户名
    password      VARCHAR(255),             -- 数据库密码
    connection_url VARCHAR(1024),            -- 数据库连接URL
    status        INTEGER,                  -- 数据源状态（如：启用、禁用等）
    test_status   INTEGER,                  -- 连接测试状态（如：成功、失败等）
    description   TEXT                      -- 数据源描述信息
);

COMMENT ON TABLE agent_datasource IS 'Agent数据源配置表';
COMMENT ON COLUMN agent_datasource.id IS '数据源ID（主键）';
COMMENT ON COLUMN agent_datasource.name IS '数据源名称';
COMMENT ON COLUMN agent_datasource.type IS '数据库类型（如：MySQL、PostgresSQL、Oracle等）';
COMMENT ON COLUMN agent_datasource.host IS '数据库主机地址';
COMMENT ON COLUMN agent_datasource.port IS '数据库端口号';
COMMENT ON COLUMN agent_datasource.database_name IS '数据库名称';
COMMENT ON COLUMN agent_datasource.username IS '数据库用户名';
COMMENT ON COLUMN agent_datasource.password IS '数据库密码';
COMMENT ON COLUMN agent_datasource.connection_url IS '数据库连接URL';
COMMENT ON COLUMN agent_datasource.status IS '数据源状态（如：启用、禁用等）';
COMMENT ON COLUMN agent_datasource.test_status IS '连接测试状态（如：成功、失败等）';
COMMENT ON COLUMN agent_datasource.description IS '数据源描述信息';
