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
    session_id                    VARCHAR(32) NOT NULL PRIMARY KEY COMMENT '会话ID',
    session_name                  VARCHAR(255) COMMENT '会话名称',
    datasource_id                 VARCHAR(32) COMMENT '创建会话时选的数据源ID',
    stream_done   INT NOT NULL DEFAULT 0 COMMENT '对话流正常结束累计次数',
    named_stream  INT NOT NULL DEFAULT 0 COMMENT '上次成功自动命名时的 stream_done'
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

CREATE TABLE export_job
(
    id              VARCHAR(32)  NOT NULL PRIMARY KEY COMMENT '导出任务ID',
    session_id      VARCHAR(32)  NOT NULL COMMENT '聊天会话ID',
    format          VARCHAR(8)   NOT NULL COMMENT '导出格式：CSV / XLSX',
    max_rows        INT          NOT NULL COMMENT '用户请求的最大行数（已钳制）',
    submitted_sql   TEXT         NOT NULL COMMENT '用户提交的 SQL',
    executed_sql    TEXT         NOT NULL COMMENT '经行数限制改写后的执行 SQL',
    status          VARCHAR(16)  NOT NULL COMMENT 'PENDING/RUNNING/READY/FAILED/EXPIRED',
    serva_file_id   VARCHAR(128) NULL COMMENT 'Serva 文件存储返回的 fileId',
    row_count       BIGINT       NULL COMMENT '实际导出行数',
    error_message   TEXT         NULL COMMENT '失败原因',
    created_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    expires_at      DATETIME     NOT NULL COMMENT '过期时间',
    finished_at     DATETIME     NULL COMMENT '完成时间'
) COMMENT = '异步导出任务表';

CREATE INDEX idx_export_job_session ON export_job (session_id);
CREATE INDEX idx_export_job_status ON export_job (status);
CREATE INDEX idx_export_job_expires ON export_job (expires_at);
