package com.zora.service;

import java.util.Map;

/**
 * 智能推荐服务接口
 * <p>
 * Phase 4 智能推荐 —— 基于用户历史对话内容和行为数据，
 * 推荐相关对话、建议问题和热门知识库。
 * </p>
 */
public interface RecommendService {

    /**
     * 获取个性化推荐
     * <p>包含三个推荐维度：
     * <ul>
     * <li>relatedConversations: 相关的历史对话（基于内容相似度）</li>
     * <li>suggestedQuestions: 基于主题匹配的建议问题</li>
     * <li>popularKnowledge: 热门/常用的知识库</li>
     * </ul>
     * </p>
     *
     * @param email 当前用户邮箱
     * @return 推荐结果 Map
     */
    Map<String, Object> getRecommendations(String email);
}
