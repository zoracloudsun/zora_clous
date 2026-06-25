package com.zora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zora.entity.ChatMessage;
import com.zora.entity.SearchResult;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * AI 对话消息 Mapper
 * <p>
 * 继承 BaseMapper 获得自动 CRUD。Phase 4 新增：
 * <ul>
 * <li>全文搜索方法（配合 ChatMessageMapper.xml）</li>
 * <li>数据统计聚合查询（配合 ChatMessageMapper.xml）</li>
 * </ul>
 * </p>
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * MySQL FULLTEXT 全文搜索消息内容
     *
     * @param userId  用户 ID（数据隔离）
     * @param keyword 搜索关键词（boolean mode）
     * @param offset  分页偏移量
     * @param limit   每页数量
     * @return 搜索结果列表（含相关性分数）
     */
    List<SearchResult> fulltextSearch(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword,
            @Param("offset") long offset,
            @Param("limit") int limit);

    /**
     * 全文搜索结果总数（用于分页）
     */
    long fulltextSearchCount(
            @Param("userId") Integer userId,
            @Param("keyword") String keyword);

    /**
     * 按天统计消息数量（最近 N 天），按 role 分组
     * 返回每行包含 date、role、count 三列
     */
    List<Map<String, Object>> countMessagesByDay(
            @Param("userId") Integer userId,
            @Param("days") int days);

    /**
     * 按小时统计消息数量（最近 90 天）
     * 返回每行包含 hour、count 两列
     */
    List<Map<String, Object>> countMessagesByHour(@Param("userId") Integer userId);

    /**
     * 统计用户每种角色的消息总数
     * 返回每行包含 role、count 两列
     */
    List<Map<String, Object>> countMessagesByRole(@Param("userId") Integer userId);
}
