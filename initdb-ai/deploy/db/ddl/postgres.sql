create table agent_datasource
(
    id             varchar(32) not null primary key,
    name           varchar(255),
    type           varchar(32),
    host           varchar(16),
    port           varchar(32),
    database_name  varchar(255),
    username       varchar(255),
    password       varchar(255),
    connection_url varchar(1024),
    status         integer,
    test_status    integer,
    description    text
);

comment on table agent_datasource is 'Agent数据源配置表';
comment on column agent_datasource.id is '数据源ID（主键）';
comment on column agent_datasource.name is '数据源名称';
comment on column agent_datasource.type is '数据库类型（如：MySQL、PostgresSQL、Oracle等）';
comment on column agent_datasource.host is '数据库主机地址';
comment on column agent_datasource.port is '数据库端口号';
comment on column agent_datasource.database_name is '数据库名称';
comment on column agent_datasource.username is '数据库用户名';
comment on column agent_datasource.password is '数据库密码';
comment on column agent_datasource.connection_url is '数据库连接URL';
comment on column agent_datasource.status is '数据源状态（如：启用、禁用等）';
comment on column agent_datasource.test_status is '连接测试状态（如：成功、失败等）';
comment on column agent_datasource.description is '数据源描述信息';

create table chat_session
(
    session_id                     varchar(32) not null primary key,
    session_name                   varchar(255),
    datasource_id                  varchar(32),
    stream_done   integer not null default 0,
    named_stream  integer not null default 0
);

comment on table chat_session is '聊天会话表';
comment on column chat_session.session_id is '会话ID';
comment on column chat_session.session_name is '会话名称';
comment on column chat_session.datasource_id is '创建会话时选的数据源ID';
comment on column chat_session.stream_done is '对话流正常结束累计次数';
comment on column chat_session.named_stream is '上次成功自动命名时的 stream_done';

CREATE TABLE agent_knowledge
(
    id               VARCHAR(32) PRIMARY KEY,
    datasource_id    VARCHAR(32),
    title            VARCHAR(500),
    type             VARCHAR(20),
    question         TEXT,
    content          TEXT,
    is_recall        INTEGER,
    embedding_status INTEGER,
    error_msg        TEXT,
    file_id          VARCHAR(64),
    file_size        BIGINT,
    file_type        VARCHAR(50),
    splitter_type    VARCHAR(20)
);

COMMENT ON TABLE agent_knowledge IS '智能体知识表';
COMMENT ON COLUMN agent_knowledge.id IS '知识ID（主键）';
COMMENT ON COLUMN agent_knowledge.datasource_id IS '数据源ID';
COMMENT ON COLUMN agent_knowledge.title IS '文档标题';
COMMENT ON COLUMN agent_knowledge.type IS '知识类型：DOCUMENT/QA/FAQ';
COMMENT ON COLUMN agent_knowledge.question IS '问题内容（FAQ/QA类型时使用）';
COMMENT ON COLUMN agent_knowledge.content IS '答案内容（QA/FAQ类型时使用）';
COMMENT ON COLUMN agent_knowledge.is_recall IS '业务状态：1=召回, 0=非召回';
COMMENT ON COLUMN agent_knowledge.embedding_status IS '向量化状态：0=待处理, 1=处理中, 2=已完成, 3=失败';
COMMENT ON COLUMN agent_knowledge.error_msg IS '错误信息（操作失败时记录）';
COMMENT ON COLUMN agent_knowledge.file_id IS '文件ID';
COMMENT ON COLUMN agent_knowledge.file_size IS '文件大小（字节）';
COMMENT ON COLUMN agent_knowledge.file_type IS '文件类型';
COMMENT ON COLUMN agent_knowledge.splitter_type IS '分块策略类型：token/recursive/sentence/paragraph/semantic';

create table export_job
(
    id              varchar(32)  not null primary key,
    session_id      varchar(32)  not null,
    format          varchar(8)   not null,
    max_rows        integer      not null,
    submitted_sql   text         not null,
    executed_sql    text         not null,
    status          varchar(16)  not null,
    serva_file_id   varchar(128),
    row_count       bigint,
    error_message   text,
    created_time    timestamp    not null default now(),
    expires_at      timestamp    not null,
    finished_at     timestamp
);

comment on table export_job is '异步导出任务表';
comment on column export_job.id is '导出任务ID';
comment on column export_job.session_id is '聊天会话ID';
comment on column export_job.format is '导出格式：CSV / XLSX';
comment on column export_job.max_rows is '用户请求的最大行数（已钳制）';
comment on column export_job.submitted_sql is '用户提交的 SQL';
comment on column export_job.executed_sql is '经行数限制改写后的执行 SQL';
comment on column export_job.status is 'PENDING/RUNNING/READY/FAILED/EXPIRED';
comment on column export_job.serva_file_id is 'Serva 文件存储返回的 fileId';
comment on column export_job.row_count is '实际导出行数';
comment on column export_job.error_message is '失败原因';
comment on column export_job.created_time is '创建时间';
comment on column export_job.expires_at is '过期时间';
comment on column export_job.finished_at is '完成时间';

create index idx_export_job_session on export_job (session_id);
create index idx_export_job_status on export_job (status);
create index idx_export_job_expires on export_job (expires_at);