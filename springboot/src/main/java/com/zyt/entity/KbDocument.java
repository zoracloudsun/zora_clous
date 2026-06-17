package com.zyt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * RAG 知识库文档实体
 * <p>
 * 记录上传到知识库的每个文档及其处理状态。
 * 文档处理流程：PENDING → PROCESSING → COMPLETED（或 FAILED）。
 * </p>
 */
@TableName("kb_document")
@Schema(description = "RAG 知识库文档")
public class KbDocument {

    /** 待处理（刚上传，尚未开始处理） */
    public static final String STATUS_PENDING = "PENDING";
    /** 处理中（正在解析、分块、嵌入） */
    public static final String STATUS_PROCESSING = "PROCESSING";
    /** 处理完成（已成功嵌入向量库） */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /** 处理失败（解析或嵌入出错） */
    public static final String STATUS_FAILED = "FAILED";

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "文档 ID（自增主键）", example = "1")
    private Long id;

    @TableField(value = "kb_id")
    @Schema(description = "所属知识库 ID", example = "1")
    private Long kbId;

    @TableField(value = "filename")
    @Schema(description = "原始文件名", example = "Spring Boot 入门指南.pdf")
    private String filename;

    @TableField(value = "file_type")
    @Schema(description = "文件类型：pdf / docx / txt / md", example = "pdf")
    private String fileType;

    @TableField(value = "file_size")
    @Schema(description = "文件大小（字节）", example = "102400")
    private Long fileSize;

    @TableField(value = "file_path")
    @Schema(description = "文件存储路径（本地磁盘路径）")
    private String filePath;

    @TableField(value = "status")
    @Schema(description = "处理状态：PENDING / PROCESSING / COMPLETED / FAILED", example = "COMPLETED")
    private String status;

    @TableField(value = "error_message")
    @Schema(description = "处理失败时的错误信息")
    private String errorMessage;

    @TableField(value = "chunk_count")
    @Schema(description = "文本块数量", example = "15")
    private Integer chunkCount;

    @TableField(value = "created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(value = "deleted_at")
    @Schema(description = "软删除时间（null 表示未删除）")
    private LocalDateTime deletedAt;

    public KbDocument() {}

    public KbDocument(Long kbId, String filename, String fileType, Long fileSize, String filePath) {
        this.kbId = kbId;
        this.filename = filename;
        this.fileType = fileType;
        this.fileSize = fileSize;
        this.filePath = filePath;
        this.status = STATUS_PENDING;
        this.chunkCount = 0;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getKbId() { return kbId; }
    public void setKbId(Long kbId) { this.kbId = kbId; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer chunkCount) { this.chunkCount = chunkCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
