package com.zora.service;

import java.util.Map;

/**
 * 全文搜索服务接口
 * <p>
 * Phase 4 全文搜索引擎 —— 基于 MySQL FULLTEXT + ngram parser 实现中文全文搜索。
 * 支持跨对话搜索消息内容，返回按相关性排序的结果列表，关键词自动高亮。
 * </p>
 */
public interface SearchService {

    /**
     * 全文搜索消息内容
     *
     * @param email   当前用户邮箱
     * @param keyword 搜索关键词（最大 200 字符）
     * @param page    页码（从 1 开始）
     * @param size    每页数量（默认 20，最大 50）
     * @return 分页结果 Map：{ total, page, size, list: [SearchResult] }
     */
    Map<String, Object> searchMessages(String email, String keyword, int page, int size);
}
