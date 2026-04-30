CREATE TABLE chat_session
(
    session_id   VARCHAR(255) PRIMARY KEY, -- 会话ID
    session_name VARCHAR(255),             -- 会话名称
    host         VARCHAR(255),             -- 数据库主机地址
    port         VARCHAR(50),              -- 数据库端口
    url          VARCHAR(500),             -- 数据库连接URL
    username     VARCHAR(255),             -- 数据库用户名
    password     VARCHAR(255)              -- 数据库密码
);

COMMENT
ON TABLE chat_session IS '聊天会话表';
COMMENT
ON COLUMN chat_session.session_id IS '会话ID';
COMMENT
ON COLUMN chat_session.session_name IS '会话名称';
COMMENT
ON COLUMN chat_session.host IS '数据库主机地址';
COMMENT
ON COLUMN chat_session.port IS '数据库端口';
COMMENT
ON COLUMN chat_session.url IS '数据库连接URL';
COMMENT
ON COLUMN chat_session.username IS '数据库用户名';
COMMENT
ON COLUMN chat_session.password IS '数据库密码';
