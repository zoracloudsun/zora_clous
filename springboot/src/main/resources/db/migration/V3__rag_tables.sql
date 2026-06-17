-- ============================================================================
-- RAG 知识库模块数据库迁移（Phase 2）
-- 新增 knowledge_base（知识库）、kb_document（文档）、kb_chunk（文本块）三张表
-- ============================================================================

-- 知识库表：用户创建的文档集合，一个用户可拥有多个知识库
CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '知识库 ID',
    user_id     INT NOT NULL COMMENT '所属用户 ID（外键 → user.id）',
    name        VARCHAR(200) NOT NULL COMMENT '知识库名称',
    description VARCHAR(500) DEFAULT '' COMMENT '知识库描述',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    deleted_at  DATETIME DEFAULT NULL COMMENT '软删除时间（NULL=未删除）',
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识库表';

-- 知识库文档表：记录每个上传的文档及其处理状态
CREATE TABLE IF NOT EXISTS kb_document (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '文档 ID',
    kb_id             BIGINT NOT NULL COMMENT '所属知识库 ID（外键 → knowledge_base.id）',
    filename          VARCHAR(500) NOT NULL COMMENT '原始文件名',
    file_type         VARCHAR(20) NOT NULL COMMENT '文件类型：pdf / docx / txt / md',
    file_size         BIGINT DEFAULT 0 COMMENT '文件大小（字节）',
    file_path         VARCHAR(1000) NOT NULL COMMENT '文件存储路径（本地磁盘路径）',
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING / PROCESSING / COMPLETED / FAILED',
    error_message     VARCHAR(1000) DEFAULT NULL COMMENT '处理失败时的错误信息',
    chunk_count       INT DEFAULT 0 COMMENT '文本块数量',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    deleted_at        DATETIME DEFAULT NULL COMMENT '软删除时间（NULL=未删除）',
    FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识库文档表';

-- 知识库文本块表：存储文档分割后的文本块，每个块对应向量库中的一个向量
CREATE TABLE IF NOT EXISTS kb_chunk (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '块 ID',
    document_id   BIGINT NOT NULL COMMENT '所属文档 ID（外键 → kb_document.id）',
    chunk_index   INT NOT NULL COMMENT '块在文档中的序号（从 0 开始）',
    content       TEXT NOT NULL COMMENT '文本块内容',
    char_count    INT DEFAULT 0 COMMENT '字符数',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (document_id) REFERENCES kb_document(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG 知识库文本块表';

-- 索引：加速按用户查询知识库、按知识库查询文档、按文档查询块
CREATE INDEX idx_kb_user ON knowledge_base(user_id, deleted_at);
CREATE INDEX idx_kb_doc_kb ON kb_document(kb_id, deleted_at);
CREATE INDEX idx_kb_chunk_doc ON kb_chunk(document_id);
