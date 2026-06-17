package com.zora.service;

/**
 * RAG 文档处理服务接口
 * <p>
 * 负责文档解析、文本分块、向量化嵌入、向量存储的完整管道。
 * </p>
 */
public interface RagProcessingService {

    /**
     * 处理单个文档：解析 → 分块 → 嵌入 → 存储
     * <p>
     * 异步执行，处理完成后更新文档状态为 COMPLETED 或 FAILED。
     * </p>
     *
     * @param documentId 文档 ID
     */
    void processDocument(Long documentId);

    /**
     * 从 MySQL kb_chunk 表重建整个向量索引
     * <p>
     * 应用启动时调用（@PostConstruct），恢复所有已完成文档的向量嵌入。
     * </p>
     */
    void rebuildEmbeddingStore();
}
