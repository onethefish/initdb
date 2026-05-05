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

CREATE TABLE agent_knowledge
(
    id               VARCHAR(32) PRIMARY KEY COMMENT '知识ID（主键）',
    datasource_id    VARCHAR(32) COMMENT '数据源ID',
    title            VARCHAR(500) COMMENT '文档标题',
    type             VARCHAR(20) COMMENT '知识类型：DOCUMENT/QA/FAQ',
    question         TEXT COMMENT '问题内容（FAQ/QA类型时使用）',
    content          TEXT COMMENT '答案内容（QA/FAQ类型时使用）',
    is_recall        INTEGER COMMENT '业务状态：1=召回, 0=非召回',
    embedding_status INTEGER COMMENT '向量化状态：0=待处理, 1=处理中, 2=已完成, 3=失败',
    error_msg        TEXT COMMENT '错误信息（操作失败时记录）',
    file_id          VARCHAR(64) COMMENT '文件ID',
    file_size        BIGINT COMMENT '文件大小（字节）',
    file_type        VARCHAR(50) COMMENT '文件类型',
    splitter_type    VARCHAR(20) COMMENT '分块策略类型：token/recursive/sentence/paragraph/semantic'
) COMMENT ='智能体知识表';
