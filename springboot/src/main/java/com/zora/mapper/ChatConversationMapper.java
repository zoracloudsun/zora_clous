package com.zora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zora.entity.ChatConversation;

/**
 * AI 对话会话 Mapper
 * 继承 BaseMapper 获得自动 CRUD，无需 XML
 */
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
