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
    session_id   varchar(32) not null primary key,
    session_name varchar(255),
    datasource_id varchar(32)
);

comment on table chat_session is '聊天会话表';
comment on column chat_session.session_id is '会话ID';
comment on column chat_session.session_name is '会话名称';
comment on column chat_session.datasource_id is '创建会话时选的数据源ID';

