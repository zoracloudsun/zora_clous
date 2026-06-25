package com.zora.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 用户行为日志实体
 * <p>
 * Phase 4 用户行为分析 —— 记录用户的关键操作（发送消息、创建对话、搜索等），
 * 用于行为分析、功能使用统计和智能推荐的数据基础。
 * 通过 AOP 切面 {@code @TrackAction} 注解自动记录，异步写入不阻塞主流程。
 * </p>
 */
@TableName("user_action_log")
@Schema(description = "用户行为日志")
public class UserActionLog {

    /** 行为类型：创建对话 */
    public static final String ACTION_CONV_CREATE = "conv_create";
    /** 行为类型：发送消息 */
    public static final String ACTION_MESSAGE_SEND = "message_send";
    /** 行为类型：搜索查询 */
    public static final String ACTION_SEARCH_QUERY = "search_query";
    /** 行为类型：知识库上传 */
    public static final String ACTION_KB_UPLOAD = "kb_upload";
    /** 行为类型：知识库检索 */
    public static final String ACTION_KB_QUERY = "kb_query";
    /** 行为类型：Agent 调用 */
    public static final String ACTION_AGENT_CALL = "agent_call";

    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "日志 ID")
    private Long id;

    @TableField(value = "user_id")
    @Schema(description = "用户 ID")
    private Integer userId;

    @TableField(value = "action")
    @Schema(description = "行为类型", example = "message_send")
    private String action;

    @TableField(value = "target_id")
    @Schema(description = "操作目标 ID")
    private Long targetId;

    @TableField(value = "detail")
    @Schema(description = "操作详情（JSON 格式）")
    private String detail;

    @TableField(value = "ip_address")
    @Schema(description = "客户端 IP")
    private String ipAddress;

    @TableField(value = "created_at")
    @Schema(description = "操作时间")
    private LocalDateTime createdAt;

    public UserActionLog() {
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public Long getTargetId() {
        return targetId;
    }

    public void setTargetId(Long targetId) {
        this.targetId = targetId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
