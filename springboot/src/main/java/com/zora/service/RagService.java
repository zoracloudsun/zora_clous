package com.zora.service;

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

    // ==================== 回收站（知识库级别）====================

    /**
     * 列出当前用户所有已软删除的知识库
     * <p>
     * 查询所有 {@code deleted_at IS NOT NULL} 的知识库，返回含文档数量的列表。
     * </p>
     *
     * @param email 当前用户邮箱
     * @return 已删除知识库列表，每项含 id、name、description、documentCount、deletedAt 等字段
     */
    List<Map<String, Object>> listDeletedKnowledgeBases(String email);

    /**
     * 从回收站恢复知识库及其所有文档
     * <p>
     * 将知识库及旗下所有文档的 {@code deleted_at} 设为 null，
     * 并重新嵌入所有文档的文本块到向量库。
     * </p>
     *
     * @param email 当前用户邮箱
     * @param kbId  要恢复的知识库 ID
     */
    void restoreKnowledgeBase(String email, Long kbId);

    /**
     * 永久删除知识库（不可逆操作）
     * <p>
     * 依次：删除旗下所有文档的磁盘文件 → 移除向量 → 物理删除 chunks →
     * 物理删除 documents → 物理删除 knowledge_base 记录。
     * </p>
     *
     * @param email 当前用户邮箱
     * @param kbId  要永久删除的知识库 ID
     */
    void permanentlyDeleteKnowledgeBase(String email, Long kbId);

    // ==================== 回收站（文档级别，按知识库）====================

    /**
     * 列出指定知识库中所有已软删除的文档
     *
     * @param email 当前用户邮箱
     * @param kbId  知识库 ID
     * @return 已删除文档列表，每项含 id、filename、fileType、fileSize、status、deletedAt 等字段
     */
    List<Map<String, Object>> listDeletedDocuments(String email, Long kbId);

    /**
     * 从回收站恢复文档
     * <p>
     * 将软删除文档的 {@code deleted_at} 设为 null，重新嵌入其文本块到向量库。
     * 若所属知识库也处于软删除状态，则一并自动恢复。
     * </p>
     *
     * @param email 当前用户邮箱
     * @param docId 要恢复的文档 ID
     */
    void restoreDocument(String email, Long docId);

    /**
     * 永久删除文档（不可逆操作）
     * <p>
     * 依次：删除磁盘文件 → 移除向量 → 物理删除 kb_chunk → 物理删除 kb_document。
     * 仅可对已软删除（在回收站中）的文档执行。
     * </p>
     *
     * @param email 当前用户邮箱
     * @param docId 要永久删除的文档 ID
     */
    void permanentlyDeleteDocument(String email, Long docId);

    /**
     * 一键清空指定知识库的文档回收站
     * <p>
     * 永久删除该知识库回收站中的所有文档。每个文档独立删除，单个失败不影响其他。
     * </p>
     *
     * @param email 当前用户邮箱
     * @param kbId  知识库 ID
     * @return 成功删除的文档数量
     */
    int emptyDocumentRecycleBin(String email, Long kbId);
}
