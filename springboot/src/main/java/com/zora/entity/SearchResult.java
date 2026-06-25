package com.zora.entity;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 搜索结果 DTO
 * <p>
 * Phase 4 全文搜索引擎 —— 用于封装 MySQL FULLTEXT 搜索的结果。
 * 包含消息的基本信息、相关性分数和高亮后的内容片段。
 * 这是一个只读数据传输对象，不持久化到数据库。
 * </p>
 */
@Schema(description = "搜索结果")
public class SearchResult {

    @Schema(description = "消息 ID", example = "1001")
    private Long messageId;

    @Schema(description = "所属对话 ID", example = "42")
    private Long conversationId;

    @Schema(description = "对话标题", example = "关于 Spring Boot 的讨论")
    private String conversationTitle;

    @Schema(description = "消息角色：user / assistant", example = "user")
    private String role;

    @Schema(description = "消息内容（原始文本）")
    private String content;

    @Schema(description = "高亮后的内容片段，关键词用 <mark> 标签包裹")
    private String highlightContent;

    @Schema(description = "消息创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "全文搜索相关性分数（MATCH...AGAINST 返回值）", example = "3.456")
    private Double relevanceScore;

    public SearchResult() {
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
    }

    public String getConversationTitle() {
        return conversationTitle;
    }

    public void setConversationTitle(String conversationTitle) {
        this.conversationTitle = conversationTitle;
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

    public String getHighlightContent() {
        return highlightContent;
    }

    public void setHighlightContent(String highlightContent) {
        this.highlightContent = highlightContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Double getRelevanceScore() {
        return relevanceScore;
    }

    public void setRelevanceScore(Double relevanceScore) {
        this.relevanceScore = relevanceScore;
    }
}
