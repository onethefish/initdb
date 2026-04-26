-- InitDB-AI 用户认证相关表结构
-- 作者: Claude
-- 日期: 2026-04-26

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    username        VARCHAR(50) NOT NULL UNIQUE COMMENT '用户名',
    password        VARCHAR(255) COMMENT '密码(BCrypt加密)',
    nickname        VARCHAR(100) COMMENT '昵称',
    email           VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone           VARCHAR(20) UNIQUE COMMENT '手机号',
    avatar          VARCHAR(255) COMMENT '头像URL',
    status          TINYINT DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    last_login_time DATETIME COMMENT '最后登录时间',
    INDEX idx_username (username),
    INDEX idx_phone (phone),
    INDEX idx_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 第三方账号绑定表
CREATE TABLE IF NOT EXISTS sys_user_oauth (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id         BIGINT NOT NULL COMMENT '用户ID',
    provider        VARCHAR(20) NOT NULL COMMENT 'OAuth提供商: WECHAT/GITHUB/GOOGLE',
    provider_user_id VARCHAR(100) NOT NULL COMMENT '第三方用户ID',
    union_id        VARCHAR(100) COMMENT '统一用户标识(微信)',
    open_id         VARCHAR(100) COMMENT '开放平台标识',
    access_token    VARCHAR(500) COMMENT '第三方访问令牌',
    refresh_token   VARCHAR(500) COMMENT '第三方刷新令牌',
    expires_at      DATETIME COMMENT '令牌过期时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '绑定时间',
    UNIQUE KEY uk_provider_user (provider, provider_user_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方账号绑定表';

-- 验证码记录表
CREATE TABLE IF NOT EXISTS sys_captcha (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    target          VARCHAR(100) NOT NULL COMMENT '目标(手机号/邮箱)',
    captcha         VARCHAR(10) NOT NULL COMMENT '验证码',
    type            VARCHAR(20) NOT NULL COMMENT '类型: LOGIN/REGISTER/RESET',
    status          TINYINT DEFAULT 0 COMMENT '状态: 0-未使用, 1-已使用',
    expire_time     DATETIME NOT NULL COMMENT '过期时间',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_target_type (target, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证码记录表';

-- 初始化管理员账号 (密码: admin123, BCrypt加密)
INSERT INTO sys_user (username, password, nickname, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iKTx5.Z5K9Q9Z8YwV3P5IhJ9F9Oa', '管理员', 1)
ON DUPLICATE KEY UPDATE username = username;
