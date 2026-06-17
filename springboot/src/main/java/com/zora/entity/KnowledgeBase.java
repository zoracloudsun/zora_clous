package com.zora.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * RAG 知识库实体
 * <p>
 * 每个用户可以创建多个知识库，每个知识库下可上传多个文档。
 * 支持软删除（deleted_at），删除后 30 天内可恢复。
 * </p>
 */
@TableName("knowledge_base")
@Schema(description = "RAG 知识库")
public class KnowledgeBase {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "知识库 ID（自增主键）", example = "1")
    private Long id;

    @TableField(value = "user_id")
    @Schema(description = "所属用户 ID", example = "1")
    private Integer userId;

    @TableField(value = "name")
    @Schema(description = "知识库名称", example = "我的学习笔记")
    private String name;

    @TableField(value = "description")
    @Schema(description = "知识库描述", example = "存放 Spring Boot 相关资料")
    private String description;

    @TableField(value = "created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(value = "updated_at")
    @Schema(description = "最后更新时间")
    private LocalDateTime updatedAt;

    @TableField(value = "deleted_at")
    @Schema(description = "软删除时间（null 表示未删除）")
    private LocalDateTime deletedAt;

    public KnowledgeBase() {
    }

    public KnowledgeBase(Integer userId, String name, String description) {
        this.userId = userId;
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
