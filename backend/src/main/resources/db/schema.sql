-- =====================================================
-- AI Chat 后端数据库初始化脚本
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4
-- =====================================================

CREATE DATABASE IF NOT EXISTS ai_chat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ai_chat;

-- ----------------------------
-- 用户表
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_user (
                                      id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
                                      username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_username (username)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 对话会话表（ChatService / SessionService）
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_chat_session (
                                              id          VARCHAR(64)  PRIMARY KEY,
    user_id     BIGINT,
    title       VARCHAR(128) DEFAULT '新会话',
    created_at  BIGINT       NOT NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 对话消息表（ChatService）
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_chat_message (
                                              id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
                                              session_id  VARCHAR(64)  NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     MEDIUMTEXT,
    msg_timestamp BIGINT     NOT NULL,
    INDEX idx_session_id (session_id),
    INDEX idx_session_ts (session_id, msg_timestamp)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Agent 消息表（AgentMemoryStore）
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_agent_message (
                                               id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
                                               user_id       BIGINT       NOT NULL,
                                               session_id    VARCHAR(64)  NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    content       MEDIUMTEXT,
    tool_calls    TEXT,
    tool_call_id  VARCHAR(128),
    seq           INT          NOT NULL DEFAULT 0,
    INDEX idx_user_session (user_id, session_id),
    INDEX idx_session_seq (session_id, seq)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- 用户工作区授权表（代码沙箱）
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_user_workspace (
                                                id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
                                                user_id         BIGINT       NOT NULL,
                                                workspace_path  VARCHAR(512) NOT NULL,
    authorized      TINYINT      NOT NULL DEFAULT 1,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
