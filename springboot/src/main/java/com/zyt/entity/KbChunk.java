package com.zyt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * RAG 知识库文本块实体
 * <p>
 * 存储文档分割后的每个文本块。每个块对应向量库中的一个向量。
 * 启动时从本表读取所有块重建向量索引。
 * </p>
 */
@TableName("kb_chunk")
@Schema(description = "RAG 知识库文本块")
public class KbChunk {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "块 ID（自增主键）", example = "1")
    private Long id;

    @TableField(value = "document_id")
    @Schema(description = "所属文档 ID", example = "1")
    private Long documentId;

    @TableField(value = "chunk_index")
    @Schema(description = "块在文档中的序号（从 0 开始）", example = "0")
    private Integer chunkIndex;

    @TableField(value = "content")
    @Schema(description = "文本块内容", example = "Spring Boot 是一个基于 Java 的开源框架...")
    private String content;

    @TableField(value = "char_count")
    @Schema(description = "字符数", example = "256")
    private Integer charCount;

    @TableField(value = "created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    public KbChunk() {}

    public KbChunk(Long documentId, Integer chunkIndex, String content, Integer charCount) {
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.charCount = charCount;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getCharCount() { return charCount; }
    public void setCharCount(Integer charCount) { this.charCount = charCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
