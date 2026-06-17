package com.zora.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * AI 对话消息实体
 * role: user（用户消息）、assistant（AI 回复）、system（系统提示，预留）
 */
@TableName("chat_message")
@Schema(description = "AI 对话消息")
public class ChatMessage {

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "消息 ID（自增主键）", example = "1")
    private Long id;

    @TableField(value = "conversation_id")
    @Schema(description = "所属对话 ID", example = "1")
    private Long conversationId;

    @TableField(value = "role")
    @Schema(description = "消息角色", example = "user", allowableValues = { "user", "assistant", "system" })
    private String role;

    @TableField(value = "content")
    @Schema(description = "消息内容", example = "你好，请介绍一下 Spring Boot")
    private String content;

    @TableField(value = "created_at")
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @TableField(value = "deleted_at")
    @Schema(description = "软删除时间（null 表示未删除）")
    private LocalDateTime deletedAt;

    public ChatMessage() {
    }

    public ChatMessage(Long conversationId, String role, String content) {
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }
}
