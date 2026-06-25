package com.zora.service;

import java.util.Map;

/**
 * 数据统计服务接口
 * <p>
 * Phase 4 对话数据分析仪表盘 —— 提供对话数据的多维度聚合统计。
 * 统计结果使用 Redis 缓存，减少数据库压力。
 * </p>
 */
public interface StatisticsService {

    /**
     * 数据总览
     * <p>包含：总会话数、总消息数、本周活跃天数、AI 使用率</p>
     */
    Map<String, Object> getOverview(String email);

    /**
     * 每日消息数趋势（默认 30 天）
     * <p>按 user/assistant 分组返回两条时间序列</p>
     */
    Map<String, Object> getMessageTrend(String email, int days);

    /**
     * 24 小时活跃度分布（最近 90 天统计）
     * <p>返回 0-23 各小时的消息数量</p>
     */
    Map<String, Object> getActiveHours(String email);

    /**
     * 每日新对话趋势（默认 30 天）
     */
    Map<String, Object> getConversationTrend(String email, int days);

    /**
     * 用户 vs AI 消息占比
     * <p>返回各 role 的消息数量，用于饼图展示</p>
     */
    Map<String, Object> getMessageRatio(String email);

    /**
     * 知识库使用统计
     * <p>包含：知识库数量、文档总数、文档分块总数</p>
     */
    Map<String, Object> getKnowledgeBaseStats(String email);

    /**
     * 功能使用排行
     * <p>统计各 action 类型的次数，按降序排列</p>
     */
    Map<String, Object> getActionRanking(String email);

    /**
     * 最近 7 天每日活跃操作次数
     * <p>用于周活跃度仪表盘展示</p>
     */
    Map<String, Object> getWeeklyActivity(String email);
}
