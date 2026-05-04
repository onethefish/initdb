CREATE TABLE chat_session
(
    session_id    varchar(32)   not null comment '会话ID',
    session_name  varchar(255)  not null comment '会话名称',
    type          varchar(32)   not null comment '数据库类型（如：mysql、postgresql等）',
    database_name varchar(255)  not null comment '数据库名称',
    schema_name   varchar(255)  not null comment 'schema',
    host          varchar(16)   not null comment '数据库主机地址',
    port          varchar(8)    not null comment '数据库端口',
    url           varchar(1024) not null comment '数据库连接URL',
    username      varchar(255)  not null comment '数据库用户名',
    password      varchar(255)  not null comment '数据库密码',
    primary key (session_id)
) comment '会话记录表';


create table agent_datasource
(
    id             varchar(32)                             not null comment '数据源ID',
    name           varchar(255)  default ''                not null comment '数据源名称',
    type           varchar(32)   default ''                not null comment '数据库类型（如：mysql、postgresql等）',
    host           varchar(8)    default ''                not null comment '数据库主机地址',
    port           varchar(8)    default ''                not null comment '数据库端口号',
    database_name  varchar(255)  default ''                not null comment '数据库名称',
    username       varchar(255)  default ''                not null comment '数据库用户名',
    password       varchar(255)  default ''                not null comment '数据库密码',
    connection_url varchar(1024) default ''                not null comment '数据库连接URL',
    status         tinyint       default 0                 not null comment '数据源状态 0:禁用 1:启用',
    test_status    tinyint       default 0                 not null comment '连接测试状态 0:未测试 1:成功 2:失败',
    description    text                                    null comment '数据源描述信息',
    create_time    datetime      default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time    datetime      default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    primary key (id)
) comment 'Agent数据源配置表';
