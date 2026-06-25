package com.zora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zora.entity.*;
import com.zora.exception.BadRequestException;
import com.zora.exception.NotFoundException;
import com.zora.mapper.*;
import com.zora.service.StatisticsService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 数据统计服务实现
 * <p>
 * Phase 4 对话数据分析仪表盘 —— 多维度聚合统计数据。
 * 统计查询结果使用 Redis 缓存，TTL 按数据变化频率分级设置。
 * </p>
 *
 * <h3>Redis 缓存策略</h3>
 * <ul>
 * <li>Overview: 1 小时（慢变化）</li>
 * <li>Message trend: 1 小时</li>
 * <li>Active hours: 4 小时（使用模式变化慢）</li>
 * <li>KB stats: 30 分钟（文档操作更频繁）</li>
 * <li>Action ranking: 1 小时</li>
 * </ul>
 *
 * <h3>缓存 Key 格式</h3>
 * {@code stats:{email}:{metricName}}
 */
@Service
public class StatisticsServiceImpl implements StatisticsService {

    private static final Logger log = LoggerFactory.getLogger(StatisticsServiceImpl.class);

    /** 默认趋势天数 */
    private static final int DEFAULT_DAYS = 30;

    /** 最大趋势天数 */
    private static final int MAX_DAYS = 365;

    /** 活跃时段统计范围（天） */
    private static final int ACTIVE_HOURS_RANGE_DAYS = 90;

    /** 知识库统计 TTL（秒） */
    private static final long TTL_KB_STATS = 1800;       // 30 分钟

    /** 高频统计 TTL（秒） */
    private static final long TTL_HOURLY = 3600;          // 1 小时

    /** 低频统计 TTL（秒） */
    private static final long TTL_DAILY = 14400;           // 4 小时

    @Resource
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private ChatConversationMapper chatConversationMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KbDocumentMapper kbDocumentMapper;

    @Resource
    private KbChunkMapper kbChunkMapper;

    @Resource
    private UserActionLogMapper userActionLogMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ObjectMapper jsonMapper = new ObjectMapper();

    // ==================== Overview ====================

    @Override
    public Map<String, Object> getOverview(String email) {
        Integer userId = resolveUserId(email);
        return getCachedOrCompute("stats:" + email + ":overview", TTL_HOURLY, () -> {
            Map<String, Object> overview = new LinkedHashMap<>();

            // 总会话数
            LambdaQueryWrapper<ChatConversation> convWrapper = new LambdaQueryWrapper<>();
            convWrapper.eq(ChatConversation::getUserId, userId)
                    .isNull(ChatConversation::getDeletedAt);
            long totalConversations = chatConversationMapper.selectCount(convWrapper);

            // 总消息数
            LambdaQueryWrapper<ChatMessage> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.isNull(ChatMessage::getDeletedAt);
            // 通过 conversation 过滤用户
            List<Long> convIds = chatConversationMapper.selectList(
                    new LambdaQueryWrapper<ChatConversation>()
                            .select(ChatConversation::getId)
                            .eq(ChatConversation::getUserId, userId)
                            .isNull(ChatConversation::getDeletedAt)
            ).stream().map(ChatConversation::getId).collect(Collectors.toList());
            long totalMessages = 0;
            if (!convIds.isEmpty()) {
                totalMessages = chatMessageMapper.selectCount(
                        new LambdaQueryWrapper<ChatMessage>()
                                .in(ChatMessage::getConversationId, convIds)
                                .isNull(ChatMessage::getDeletedAt));
            }

            // 本周活跃天数
            LocalDate today = LocalDate.now();
            LocalDate weekStart = today.with(DayOfWeek.MONDAY);
            int activeDays = 0;
            if (!convIds.isEmpty()) {
                LambdaQueryWrapper<ChatMessage> weekWrapper = new LambdaQueryWrapper<>();
                weekWrapper.in(ChatMessage::getConversationId, convIds)
                        .ge(ChatMessage::getCreatedAt, weekStart.atStartOfDay())
                        .isNull(ChatMessage::getDeletedAt);
                List<ChatMessage> weekMessages = chatMessageMapper.selectList(weekWrapper);
                activeDays = (int) weekMessages.stream()
                        .map(m -> m.getCreatedAt().toLocalDate())
                        .distinct()
                        .count();
            }

            // AI 使用率
            long aiMessages = 0;
            long userMessages = 0;
            if (!convIds.isEmpty()) {
                aiMessages = chatMessageMapper.selectCount(
                        new LambdaQueryWrapper<ChatMessage>()
                                .in(ChatMessage::getConversationId, convIds)
                                .eq(ChatMessage::getRole, "assistant")
                                .isNull(ChatMessage::getDeletedAt));
                userMessages = chatMessageMapper.selectCount(
                        new LambdaQueryWrapper<ChatMessage>()
                                .in(ChatMessage::getConversationId, convIds)
                                .eq(ChatMessage::getRole, "user")
                                .isNull(ChatMessage::getDeletedAt));
            }
            double aiRate = (userMessages + aiMessages) > 0
                    ? (double) aiMessages / (userMessages + aiMessages) * 100
                    : 0;

            overview.put("totalConversations", totalConversations);
            overview.put("totalMessages", totalMessages);
            overview.put("activeDaysThisWeek", activeDays);
            overview.put("aiUsageRate", Math.round(aiRate * 10.0) / 10.0);
            overview.put("userMessages", userMessages);
            overview.put("aiMessages", aiMessages);

            return overview;
        });
    }

    // ==================== Message Trend ====================

    @Override
    public Map<String, Object> getMessageTrend(String email, int days) {
        if (days <= 0 || days > MAX_DAYS) days = DEFAULT_DAYS;
        Integer userId = resolveUserId(email);

        final int finalDays = days;
        return getCachedOrCompute("stats:" + email + ":messageTrend:" + days, TTL_HOURLY, () -> {
            List<Map<String, Object>> rawData = chatMessageMapper.countMessagesByDay(userId, finalDays);

            // 构建完整日期序列（含空值填充）
            List<String> dateLabels = new ArrayList<>();
            List<Long> userCounts = new ArrayList<>();
            List<Long> aiCounts = new ArrayList<>();

            LocalDate today = LocalDate.now();
            for (int i = finalDays - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                dateLabels.add(dateStr);

                long uCount = 0, aCount = 0;
                for (Map<String, Object> row : rawData) {
                    if (dateStr.equals(String.valueOf(row.get("date")))) {
                        if ("user".equals(row.get("role"))) {
                            uCount = ((Number) row.get("count")).longValue();
                        } else if ("assistant".equals(row.get("role"))) {
                            aCount = ((Number) row.get("count")).longValue();
                        }
                    }
                }
                userCounts.add(uCount);
                aiCounts.add(aCount);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dates", dateLabels);
            result.put("userCounts", userCounts);
            result.put("aiCounts", aiCounts);
            result.put("days", finalDays);
            return result;
        });
    }

    // ==================== Active Hours ====================

    @Override
    public Map<String, Object> getActiveHours(String email) {
        Integer userId = resolveUserId(email);
        return getCachedOrCompute("stats:" + email + ":activeHours", TTL_DAILY, () -> {
            List<Map<String, Object>> rawData = chatMessageMapper.countMessagesByHour(userId);

            // 构建 0-23 小时完整序列
            List<Integer> hours = new ArrayList<>();
            List<Long> counts = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                hours.add(h);
                long count = 0;
                for (Map<String, Object> row : rawData) {
                    if (((Number) row.get("hour")).intValue() == h) {
                        count = ((Number) row.get("count")).longValue();
                        break;
                    }
                }
                counts.add(count);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("hours", hours);
            result.put("counts", counts);
            return result;
        });
    }

    // ==================== Conversation Trend ====================

    @Override
    public Map<String, Object> getConversationTrend(String email, int days) {
        if (days <= 0 || days > MAX_DAYS) days = DEFAULT_DAYS;
        Integer userId = resolveUserId(email);

        final int finalDays = days;
        return getCachedOrCompute("stats:" + email + ":convTrend:" + days, TTL_HOURLY, () -> {
            List<Map<String, Object>> rawData = chatConversationMapper.countConversationsByDay(userId, finalDays);

            List<String> dateLabels = new ArrayList<>();
            List<Long> convCounts = new ArrayList<>();

            LocalDate today = LocalDate.now();
            for (int i = finalDays - 1; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
                dateLabels.add(dateStr);

                long count = 0;
                for (Map<String, Object> row : rawData) {
                    if (dateStr.equals(String.valueOf(row.get("date")))) {
                        count = ((Number) row.get("count")).longValue();
                        break;
                    }
                }
                convCounts.add(count);
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dates", dateLabels);
            result.put("counts", convCounts);
            result.put("days", finalDays);
            return result;
        });
    }

    // ==================== Message Ratio ====================

    @Override
    public Map<String, Object> getMessageRatio(String email) {
        Integer userId = resolveUserId(email);
        return getCachedOrCompute("stats:" + email + ":msgRatio", TTL_HOURLY, () -> {
            List<Map<String, Object>> rawData = chatMessageMapper.countMessagesByRole(userId);

            Map<String, Object> result = new LinkedHashMap<>();
            List<Map<String, Object>> items = new ArrayList<>();
            for (Map<String, Object> row : rawData) {
                Map<String, Object> item = new LinkedHashMap<>();
                String role = String.valueOf(row.get("role"));
                item.put("name", "user".equals(role) ? "用户消息" : "AI 回复");
                item.put("value", ((Number) row.get("count")).longValue());
                items.add(item);
            }
            result.put("items", items);
            return result;
        });
    }

    // ==================== Knowledge Base Stats ====================

    @Override
    public Map<String, Object> getKnowledgeBaseStats(String email) {
        Integer userId = resolveUserId(email);
        return getCachedOrCompute("stats:" + email + ":kbStats", TTL_KB_STATS, () -> {
            // 知识库数量
            LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
            kbWrapper.eq(KnowledgeBase::getUserId, userId)
                    .isNull(KnowledgeBase::getDeletedAt);
            long kbCount = knowledgeBaseMapper.selectCount(kbWrapper);

            // 统计文档数和分块数
            long docCount = 0;
            long chunkCount = 0;
            if (kbCount > 0) {
                List<Long> kbIds = knowledgeBaseMapper.selectList(kbWrapper).stream()
                        .map(KnowledgeBase::getId).collect(Collectors.toList());
                docCount = kbDocumentMapper.selectCount(
                        new LambdaQueryWrapper<KbDocument>()
                                .in(KbDocument::getKbId, kbIds)
                                .isNull(KbDocument::getDeletedAt));
                if (docCount > 0) {
                    List<Long> docIds = kbDocumentMapper.selectList(
                            new LambdaQueryWrapper<KbDocument>()
                                    .select(KbDocument::getId)
                                    .in(KbDocument::getKbId, kbIds)
                                    .isNull(KbDocument::getDeletedAt)
                    ).stream().map(KbDocument::getId).collect(Collectors.toList());
                    if (!docIds.isEmpty()) {
                        chunkCount = kbChunkMapper.selectCount(
                                new LambdaQueryWrapper<KbChunk>()
                                        .in(KbChunk::getDocumentId, docIds));
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("knowledgeBaseCount", kbCount);
            result.put("documentCount", docCount);
            result.put("chunkCount", chunkCount);
            return result;
        });
    }

    // ==================== Action Ranking ====================

    @Override
    public Map<String, Object> getActionRanking(String email) {
        Integer userId = resolveUserId(email);
        return getCachedOrCompute("stats:" + email + ":actionRanking", TTL_HOURLY, () -> {
            LambdaQueryWrapper<UserActionLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserActionLog::getUserId, userId);
            List<UserActionLog> logs = userActionLogMapper.selectList(wrapper);

            // 按 action 分组统计
            Map<String, Long> actionCounts = logs.stream()
                    .collect(Collectors.groupingBy(UserActionLog::getAction, Collectors.counting()));

            // 转换为友好的名称并按数量排序
            List<Map<String, Object>> ranking = actionCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(entry -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("action", entry.getKey());
                        item.put("label", getActionLabel(entry.getKey()));
                        item.put("count", entry.getValue());
                        return item;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ranking", ranking);
            return result;
        });
    }

    // ==================== Weekly Activity ====================

    @Override
    public Map<String, Object> getWeeklyActivity(String email) {
        Integer userId = resolveUserId(email);
        return getCachedOrCompute("stats:" + email + ":weeklyActivity", TTL_HOURLY, () -> {
            LocalDate today = LocalDate.now();
            LocalDate weekAgo = today.minusDays(6);

            // 查询最近 7 天的行为日志
            LambdaQueryWrapper<UserActionLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserActionLog::getUserId, userId)
                    .ge(UserActionLog::getCreatedAt, weekAgo.atStartOfDay());
            List<UserActionLog> logs = userActionLogMapper.selectList(wrapper);

            // 按天统计
            Map<LocalDate, Long> dailyCounts = logs.stream()
                    .collect(Collectors.groupingBy(
                            log -> log.getCreatedAt().toLocalDate(),
                            Collectors.counting()));

            List<String> dates = new ArrayList<>();
            List<Long> counts = new ArrayList<>();
            for (int i = 6; i >= 0; i--) {
                LocalDate date = today.minusDays(i);
                dates.add(date.format(DateTimeFormatter.ISO_LOCAL_DATE));
                counts.add(dailyCounts.getOrDefault(date, 0L));
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dates", dates);
            result.put("counts", counts);
            return result;
        });
    }

    // ==================== Private Helpers ====================

    /**
     * 根据邮箱获取用户 ID
     */
    private Integer resolveUserId(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user.getId();
    }

    /**
     * Redis 缓存优先查询
     * <p>命中缓存直接返回，未命中则执行 supplier 查询并缓存结果。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getCachedOrCompute(String key, long ttlSeconds,
                                                    Supplier<Map<String, Object>> supplier) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return jsonMapper.readValue(cached, new TypeReference<Map<String, Object>>() {});
            }
        } catch (Exception e) {
            log.warn("Redis 缓存读取失败: key={}, error={}", key, e.getMessage());
        }

        Map<String, Object> result = supplier.get();

        try {
            stringRedisTemplate.opsForValue().set(key,
                    jsonMapper.writeValueAsString(result),
                    ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis 缓存写入失败: key={}, error={}", key, e.getMessage());
        }

        return result;
    }

    /**
     * 将 action 类型转换为中文标签
     */
    private String getActionLabel(String action) {
        switch (action) {
            case UserActionLog.ACTION_CONV_CREATE: return "创建对话";
            case UserActionLog.ACTION_MESSAGE_SEND: return "发送消息";
            case UserActionLog.ACTION_SEARCH_QUERY: return "搜索查询";
            case UserActionLog.ACTION_KB_UPLOAD: return "知识库上传";
            case UserActionLog.ACTION_KB_QUERY: return "知识库检索";
            case UserActionLog.ACTION_AGENT_CALL: return "Agent 调用";
            default: return action;
        }
    }
}
