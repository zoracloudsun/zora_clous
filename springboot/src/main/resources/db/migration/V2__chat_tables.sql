-- ============================================================================
-- AI 对话模块数据库迁移（Phase 1 + P1 安全加固）
-- 新增 chat_conversation（对话会话）和 chat_message（对话消息）两张表
-- 包含 P1-6 软删除支持
-- ============================================================================

CREATE TABLE IF NOT EXISTS chat_conversation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '会话 ID',
    user_id     INT NOT NULL COMMENT '用户 ID（外键 → user.id）',
    title       VARCHAR(200) DEFAULT '新的对话' COMMENT '会话标题（可由 AI 自动生成）',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    deleted_at  DATETIME DEFAULT NULL COMMENT 'P1-6: 软删除时间（NULL=未删除）',
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话会话表';

CREATE TABLE IF NOT EXISTS chat_message (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息 ID',
    conversation_id  BIGINT NOT NULL COMMENT '所属会话 ID（外键 → chat_conversation.id）',
    role             VARCHAR(20) NOT NULL COMMENT '消息角色：user / assistant / system',
    content          TEXT NOT NULL COMMENT '消息内容（Markdown 格式）',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '消息时间',
    deleted_at       DATETIME DEFAULT NULL COMMENT 'P1-6: 软删除时间（NULL=未删除）',
    FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 对话消息表';

-- 索引：按会话查询消息（按时间排序）
CREATE INDEX idx_chat_message_conv ON chat_message(conversation_id, created_at);
-- P1-6: 软删除过滤索引
CREATE INDEX idx_chat_conv_not_deleted ON chat_conversation(user_id, deleted_at);
