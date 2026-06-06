package com.zyt.service;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 对话服务接口
 */
public interface AiChatService {

    /** SSE 流式对话 */
    Flux<String> streamChat(String email, String userMessage, Long conversationId);

    /** 获取当前用户的对话列表 */
    List<Map<String, Object>> listConversations(String email);

    /** 获取对话的消息历史 */
    List<Map<String, Object>> getMessages(String email, Long conversationId);

    /** 软删除对话 */
    void deleteConversation(String email, Long conversationId);

    /** 创建新对话 */
    Map<String, Object> createNewConversation(String email, String title);

    /** 获取回收站列表（已软删除的对话，30 天内） */
    List<Map<String, Object>> listDeletedConversations(String email);

    /** 恢复已软删除的对话 */
    void restoreConversation(String email, Long conversationId);

    /** 从回收站永久删除 */
    void permanentDeleteConversation(String email, Long conversationId);

    /** 定期清理：物理删除超过 30 天的软删除记录 */
    int cleanupOldDeletedConversations();
}
