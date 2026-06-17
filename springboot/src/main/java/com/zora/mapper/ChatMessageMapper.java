package com.zora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zora.entity.ChatMessage;

/**
 * AI 对话消息 Mapper
 * 继承 BaseMapper 获得自动 CRUD，无需 XML
 */
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
