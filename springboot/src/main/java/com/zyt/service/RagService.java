package com.zyt.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库服务接口
 * <p>
 * 提供知识库 CRUD、文档管理和知识检索功能。
 * </p>
 */
public interface RagService {

    // ==================== 知识库 CRUD ====================

    /** 创建知识库 */
    Map<String, Object> createKnowledgeBase(String email, String name, String description);

    /** 列出当前用户的所有知识库（含文档数量） */
    List<Map<String, Object>> listKnowledgeBases(String email);

    /** 获取知识库详情（含文档列表） */
    Map<String, Object> getKnowledgeBase(String email, Long kbId);

    /** 更新知识库名称和描述 */
    void updateKnowledgeBase(String email, Long kbId, String name, String description);

    /** 软删除知识库及其所有文档、文本块 */
    void deleteKnowledgeBase(String email, Long kbId);

    // ==================== 文档管理 ====================

    /** 上传文档到指定知识库（保存到磁盘 + 创建数据库记录 + 异步触发处理） */
    Map<String, Object> uploadDocument(String email, Long kbId, MultipartFile file);

    /** 列出知识库中的所有文档 */
    List<Map<String, Object>> listDocuments(String email, Long kbId);

    /** 软删除指定文档及其所有文本块，同时从向量库中移除 */
    void deleteDocument(String email, Long kbId, Long docId);

    // ==================== 检索查询 ====================

    /**
     * 在指定知识库中检索相关文本块
     *
     * @param kbId       知识库 ID
     * @param query      查询文本
     * @param maxResults 最大返回结果数
     * @param minScore   最低相关性分数（0~1）
     * @return 检索结果列表，每项包含 content、filename、score 等
     */
    List<Map<String, Object>> searchChunks(Long kbId, String query, int maxResults, double minScore);

    /**
     * 检索并格式化为上下文字符串（供 AI 对话使用）
     *
     * @param kbId       知识库 ID
     * @param query      用户问题
     * @param maxResults 最大返回结果数
     * @param minScore   最低相关性分数
     * @return 格式化的上下文字符串，无相关结果时返回空字符串
     */
    String retrieveContext(Long kbId, String query, int maxResults, double minScore);

    // ==================== 定期清理 ====================

    /** 物理删除超过 30 天的软删除知识库/文档/块 */
    int cleanupOldDeletedRecords();
}
