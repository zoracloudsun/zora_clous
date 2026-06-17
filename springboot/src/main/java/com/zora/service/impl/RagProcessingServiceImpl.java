package com.zora.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zora.entity.KbChunk;
import com.zora.entity.KbDocument;
import com.zora.mapper.KbChunkMapper;
import com.zora.mapper.KbDocumentMapper;
import com.zora.mapper.KnowledgeBaseMapper;
import com.zora.service.RagProcessingService;
import com.zora.utils.TextSplitterUtil;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * RAG 文档处理服务实现
 * <p>
 * 实现文档处理的完整管道：解析 → 分块 → 嵌入 → 存储。
 * 支持应用启动时从 MySQL 重建向量索引。
 * </p>
 *
 * <h3>处理管道流程</h3>
 * <ol>
 * <li>使用 Apache Tika 解析文档文件（支持 PDF/DOCX/DOC/TXT/MD 等）</li>
 * <li>递归分割为文本块（最大 800 字符，重叠 100 字符）</li>
 * <li>调用 EmbeddingModel 将每个文本块转为向量</li>
 * <li>向量存入 SimpleEmbeddingStore，文本块存入 kb_chunk 表</li>
 * <li>更新文档状态为 COMPLETED</li>
 * </ol>
 */
@Service
public class RagProcessingServiceImpl implements RagProcessingService {

    private static final Logger log = LoggerFactory.getLogger(RagProcessingServiceImpl.class);

    /** Apache Tika 实例（线程安全，可复用） */
    private static final Tika TIKA = new Tika();

    @Resource
    private KbDocumentMapper documentMapper;

    @Resource
    private KbChunkMapper chunkMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private SimpleEmbeddingStore embeddingStore;

    @Value("${rag.document.max-chunk-size:800}")
    private int maxChunkSize;

    @Value("${rag.document.max-chunk-overlap:100}")
    private int maxChunkOverlap;

    /**
     * 应用启动时从 MySQL 重建内存向量索引
     * <p>
     * 遍历所有状态为 COMPLETED 且未删除的文档，加载其文本块，
     * 重新嵌入并存入向量存储。
     * </p>
     */
    @Override
    @PostConstruct
    public void rebuildEmbeddingStore() {
        log.info("开始从 MySQL 重建向量索引...");
        long startTime = System.currentTimeMillis();

        // 查询所有已完成且未删除的文档
        LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(KbDocument::getStatus, KbDocument.STATUS_COMPLETED)
                .isNull(KbDocument::getDeletedAt);
        List<KbDocument> completedDocs = documentMapper.selectList(docWrapper);

        if (completedDocs.isEmpty()) {
            log.info("向量索引重建完成：无已完成文档需要恢复");
            return;
        }

        int totalChunks = 0;
        for (KbDocument doc : completedDocs) {
            try {
                // 加载该文档的所有文本块（按序号排序）
                LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
                chunkWrapper.eq(KbChunk::getDocumentId, doc.getId())
                        .orderByAsc(KbChunk::getChunkIndex);
                List<KbChunk> chunks = chunkMapper.selectList(chunkWrapper);

                if (chunks.isEmpty()) {
                    log.warn("文档 id={} 状态为 COMPLETED 但无文本块，跳过", doc.getId());
                    continue;
                }

                // 批量嵌入并存入向量库
                for (KbChunk chunk : chunks) {
                    try {
                        dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(chunk.getContent())
                                .content();
                        Metadata metadata = new Metadata()
                                .put("document_id", String.valueOf(doc.getId()))
                                .put("kb_id", String.valueOf(doc.getKbId()))
                                .put("filename", doc.getFilename())
                                .put("chunk_index", String.valueOf(chunk.getChunkIndex()));
                        TextSegment segment = TextSegment.from(chunk.getContent(), metadata);
                        embeddingStore.add(embedding, segment);
                        totalChunks++;
                    } catch (Exception e) {
                        log.error("嵌入文本块失败: doc={}, chunk={}, error={}",
                                doc.getFilename(), chunk.getChunkIndex(), e.getMessage());
                    }
                }
                log.debug("已恢复文档: {} ({} 个文本块)", doc.getFilename(), chunks.size());
            } catch (Exception e) {
                log.error("恢复文档 {} 时出错: {}", doc.getFilename(), e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("向量索引重建完成：恢复了 {} 个文档的 {} 个文本块，耗时 {}ms",
                completedDocs.size(), totalChunks, elapsed);
    }

    /**
     * 异步处理文档
     */
    @Override
    @Async
    public void processDocument(Long documentId) {
        KbDocument kbDoc = documentMapper.selectById(documentId);
        if (kbDoc == null) {
            log.error("文档 id={} 不存在，无法处理", documentId);
            return;
        }

        // 更新状态为 PROCESSING
        kbDoc.setStatus(KbDocument.STATUS_PROCESSING);
        documentMapper.updateById(kbDoc);

        try {
            log.info("开始处理文档: {} (id={})", kbDoc.getFilename(), documentId);

            // 1. 使用 Apache Tika 提取文本内容
            Path filePath = Paths.get(kbDoc.getFilePath());
            if (!Files.exists(filePath)) {
                throw new RuntimeException("文件不存在: " + filePath);
            }

            String fullText;
            try (InputStream is = Files.newInputStream(filePath)) {
                fullText = TIKA.parseToString(is);
            }

            if (fullText == null || fullText.isBlank()) {
                throw new RuntimeException("文档内容为空或无法解析");
            }
            log.debug("文档解析完成，总字符数: {}", fullText.length());

            // 2. 分块：使用自实现的递归文本分割器
            List<String> chunkTexts = TextSplitterUtil.split(fullText, maxChunkSize, maxChunkOverlap);
            log.debug("文档分块完成，共 {} 个文本块", chunkTexts.size());

            // 3. 逐块嵌入并存储
            int chunkIndex = 0;
            for (String chunkText : chunkTexts) {
                if (chunkText.isBlank())
                    continue;

                // 保存到 kb_chunk 表
                KbChunk chunk = new KbChunk(
                        documentId, chunkIndex, chunkText, chunkText.length());
                chunkMapper.insert(chunk);

                // 嵌入并存入向量库
                try {
                    dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(chunkText).content();

                    Metadata metadata = new Metadata()
                            .put("document_id", String.valueOf(documentId))
                            .put("kb_id", String.valueOf(kbDoc.getKbId()))
                            .put("filename", kbDoc.getFilename())
                            .put("chunk_index", String.valueOf(chunkIndex));
                    TextSegment segment = TextSegment.from(chunkText, metadata);
                    embeddingStore.add(embedding, segment);
                } catch (Exception e) {
                    log.error("嵌入文本块失败: doc={}, chunk={}, error={}",
                            kbDoc.getFilename(), chunkIndex, e.getMessage());
                    throw e;
                }

                chunkIndex++;
            }

            // 4. 更新文档状态为 COMPLETED
            kbDoc.setStatus(KbDocument.STATUS_COMPLETED);
            kbDoc.setChunkCount(chunkIndex);
            kbDoc.setErrorMessage(null);
            documentMapper.updateById(kbDoc);

            log.info("文档处理完成: {}, 共 {} 个文本块", kbDoc.getFilename(), chunkIndex);

        } catch (Exception e) {
            log.error("文档处理失败: {} (id={}), error={}",
                    kbDoc.getFilename(), documentId, e.getMessage(), e);
            // 更新状态为 FAILED
            kbDoc.setStatus(KbDocument.STATUS_FAILED);
            kbDoc.setErrorMessage(e.getMessage() != null ? e.getMessage() : "未知错误");
            documentMapper.updateById(kbDoc);
        }
    }
}
