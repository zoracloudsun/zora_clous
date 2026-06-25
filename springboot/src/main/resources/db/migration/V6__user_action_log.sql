-- V6__user_action_log.sql
-- Phase 4: 用户行为分析
-- 用户行为日志表，记录关键操作用于行为分析和推荐

CREATE TABLE IF NOT EXISTS user_action_log (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志 ID',
    user_id      INT NOT NULL COMMENT '用户 ID（外键 → user.id）',
    action       VARCHAR(50) NOT NULL COMMENT '行为类型：conv_create, message_send, search_query, kb_upload, kb_query, agent_call',
    target_id    BIGINT DEFAULT NULL COMMENT '操作目标 ID（对话 ID、知识库 ID 等）',
    detail       VARCHAR(500) DEFAULT NULL COMMENT '操作详情（JSON 格式，如搜索关键词、消息长度等）',
    ip_address   VARCHAR(45) DEFAULT NULL COMMENT '客户端 IP 地址',
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_action_user_time (user_id, action, created_at),
    INDEX idx_action_created (created_at),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为日志表（Phase 4 用户行为分析）';
