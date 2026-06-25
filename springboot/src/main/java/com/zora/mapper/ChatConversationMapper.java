package com.zora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zora.entity.ChatConversation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * AI 对话会话 Mapper
 * 继承 BaseMapper 获得自动 CRUD，Phase 4 新增统计查询方法
 */
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {

    /**
     * 按天统计新创建对话数量（最近 N 天）
     * 用于仪表盘对话趋势图
     */
    @Select("SELECT DATE(created_at) AS date, COUNT(*) AS count " +
            "FROM chat_conversation " +
            "WHERE user_id = #{userId} AND deleted_at IS NULL " +
            "AND created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(created_at) ORDER BY date ASC")
    List<Map<String, Object>> countConversationsByDay(
            @Param("userId") Integer userId,
            @Param("days") int days);
}
