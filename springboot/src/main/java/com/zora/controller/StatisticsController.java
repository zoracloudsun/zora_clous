package com.zora.controller;

import com.zora.service.StatisticsService;
import com.zora.utils.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 数据统计控制器
 * <p>
 * Phase 4 对话数据分析仪表盘 —— 提供对话数据的多维度统计 API。
 * 统计数据使用 Redis 缓存，按 TTL 分级管理。
 * </p>
 *
 * <h3>统计维度</h3>
 * <ul>
 * <li>数据总览：总会话数、总消息数、本周活跃天数、AI 使用率</li>
 * <li>消息趋势：每曰用户消息和 AI 回复趋势（折线图）</li>
 * <li>活跃时段：24 小时活跃度分布（柱状图）</li>
 * <li>对话趋势：每日新对话创建趋势</li>
 * <li>消息占比：用户 vs AI 消息比例（饼图）</li>
 * <li>知识库统计：KB 数量、文档数、分块数</li>
 * <li>功能排行：各功能使用次数排行（行为分析）</li>
 * <li>周活跃度：最近 7 天每日操作次数</li>
 * </ul>
 */
@RestController
@RequestMapping("/statistics")
@Tag(name = "数据统计", description = "对话数据分析仪表盘：消息趋势、活跃时段、使用占比、功能排行等可视化数据")
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    @Operation(summary = "数据总览", description = "返回总会话数、总消息数、本周活跃天数、AI 使用率等摘要指标")
    @GetMapping("/overview")
    public ResponseUtil getOverview(
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getOverview(email));
    }

    @Operation(summary = "消息趋势", description = "返回每日用户消息和 AI 回复数量趋势（默认 30 天），用于折线图展示")
    @GetMapping("/message-trend")
    public ResponseUtil getMessageTrend(
            @Parameter(description = "统计天数（默认 30，最大 365）", example = "30")
            @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "当前登录用户", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getMessageTrend(email, days));
    }

    @Operation(summary = "活跃时段", description = "返回 0-23 小时各时段的消息数量分布（最近 90 天），用于柱状图展示")
    @GetMapping("/active-hours")
    public ResponseUtil getActiveHours(
            @Parameter(description = "当前登录用户", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getActiveHours(email));
    }

    @Operation(summary = "对话趋势", description = "返回每日新对话创建数量趋势（默认 30 天），用于折线图展示")
    @GetMapping("/conversation-trend")
    public ResponseUtil getConversationTrend(
            @Parameter(description = "统计天数（默认 30，最大 365）", example = "30")
            @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "当前登录用户", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getConversationTrend(email, days));
    }

    @Operation(summary = "消息占比", description = "返回用户消息和 AI 回复的数量占比，用于饼图展示")
    @GetMapping("/message-ratio")
    public ResponseUtil getMessageRatio(
            @Parameter(description = "当前登录用户", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getMessageRatio(email));
    }

    @Operation(summary = "知识库统计", description = "返回知识库数量、文档总数、分块总数")
    @GetMapping("/knowledge-stats")
    public ResponseUtil getKnowledgeBaseStats(
            @Parameter(description = "当前登录用户", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getKnowledgeBaseStats(email));
    }

    @Operation(summary = "功能使用排行", description = "返回各功能（发送消息、搜索、Agent 等）的使用次数排行")
    @GetMapping("/action-ranking")
    public ResponseUtil getActionRanking(
            @Parameter(description = "当前登录用户", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getActionRanking(email));
    }

    @Operation(summary = "周活跃度", description = "返回最近 7 天每日操作次数，用于周活跃度图")
    @GetMapping("/weekly-activity")
    public ResponseUtil getWeeklyActivity(
            @Parameter(description = "当前登录用户", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(statisticsService.getWeeklyActivity(email));
    }
}
