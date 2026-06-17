package com.zyt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zyt.entity.*;
import com.zyt.exception.BadRequestException;
import com.zyt.exception.ForbiddenException;
import com.zyt.exception.NotFoundException;
import com.zyt.mapper.*;
import com.zyt.service.RagProcessingService;
import com.zyt.service.RagService;
import com.zyt.utils.FileTypeUtil;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 知识库服务实现
 * <p>
 * 提供知识库 CRUD、文档上传管理、知识检索的完整业务逻辑。
 * 所有操作均验证所有权（知识库和文档都属于当前用户）。
 * </p>
 *
 * <h3>文件存储</h3>
 * 上传的文件保存到配置的本地目录 {@code rag.document.upload-dir}，
 * 文件名格式：{@code {timestamp}_{原始文件名}} 防止冲突。
 */
@Service
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    @Resource
    private UserMapper userMapper;

    @Resource
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Resource
    private KbDocumentMapper documentMapper;

    @Resource
    private KbChunkMapper chunkMapper;

    @Resource
    private RagProcessingService ragProcessingService;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private SimpleEmbeddingStore embeddingStore;

    @Value("${rag.document.upload-dir:./uploads/rag}")
    private String uploadDir;

    @Value("${rag.document.max-size:10485760}")
    private long maxFileSize;

    @Value("${rag.document.max-retrieve-results:5}")
    private int maxRetrieveResults;

    @Value("${rag.document.min-relevance-score:0.3}")
    private double minRelevanceScore;

    // ==================== 知识库 CRUD ====================

    @Override
    public Map<String, Object> createKnowledgeBase(String email, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("知识库名称不能为空");
        }
        if (name.length() > 200) {
            throw new BadRequestException("知识库名称不能超过 200 个字符");
        }

        User user = findUserByEmail(email);
        KnowledgeBase kb = new KnowledgeBase(user.getId(), name.trim(),
                description != null ? description.trim() : "");
        knowledgeBaseMapper.insert(kb);

        log.info("用户 {} 创建知识库: {} (id={})", email, kb.getName(), kb.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("id", kb.getId());
        result.put("name", kb.getName());
        result.put("description", kb.getDescription());
        result.put("createdAt", kb.getCreatedAt());
        return result;
    }

    @Override
    public List<Map<String, Object>> listKnowledgeBases(String email) {
        User user = findUserByEmail(email);

        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getUserId, user.getId())
                .isNull(KnowledgeBase::getDeletedAt)
                .orderByDesc(KnowledgeBase::getUpdatedAt);
        List<KnowledgeBase> kbs = knowledgeBaseMapper.selectList(wrapper);

        return kbs.stream().map(kb -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", kb.getId());
            map.put("name", kb.getName());
            map.put("description", kb.getDescription());
            // 统计文档数量（仅已完成的）
            LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
            docWrapper.eq(KbDocument::getKbId, kb.getId())
                    .isNull(KbDocument::getDeletedAt)
                    .eq(KbDocument::getStatus, KbDocument.STATUS_COMPLETED);
            map.put("documentCount", documentMapper.selectCount(docWrapper));
            map.put("createdAt", kb.getCreatedAt());
            map.put("updatedAt", kb.getUpdatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getKnowledgeBase(String email, Long kbId) {
        KnowledgeBase kb = findKnowledgeBase(kbId, email);
        List<Map<String, Object>> documents = listDocuments(email, kbId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", kb.getId());
        result.put("name", kb.getName());
        result.put("description", kb.getDescription());
        result.put("createdAt", kb.getCreatedAt());
        result.put("updatedAt", kb.getUpdatedAt());
        result.put("documents", documents);
        return result;
    }

    @Override
    public void updateKnowledgeBase(String email, Long kbId, String name, String description) {
        KnowledgeBase kb = findKnowledgeBase(kbId, email);

        if (name != null && !name.isBlank()) {
            if (name.length() > 200) {
                throw new BadRequestException("知识库名称不能超过 200 个字符");
            }
            kb.setName(name.trim());
        }
        if (description != null) {
            kb.setDescription(description.trim());
        }
        knowledgeBaseMapper.updateById(kb);
        log.info("用户 {} 更新知识库 id={}", email, kbId);
    }

    @Override
    public void deleteKnowledgeBase(String email, Long kbId) {
        KnowledgeBase kb = findKnowledgeBase(kbId, email);

        LocalDateTime now = LocalDateTime.now();
        // 软删除知识库
        kb.setDeletedAt(now);
        knowledgeBaseMapper.updateById(kb);

        // 软删除该知识库下所有未删除的文档
        LambdaUpdateWrapper<KbDocument> docUpdate = new LambdaUpdateWrapper<>();
        docUpdate.eq(KbDocument::getKbId, kbId)
                .isNull(KbDocument::getDeletedAt)
                .set(KbDocument::getDeletedAt, now);
        documentMapper.update(null, docUpdate);

        log.info("用户 {} 删除知识库: {} (id={})", email, kb.getName(), kbId);
    }

    // ==================== 文档管理 ====================

    @Override
    public Map<String, Object> uploadDocument(String email, Long kbId, MultipartFile file) {
        // 1. 验证知识库所有权
        KnowledgeBase kb = findKnowledgeBase(kbId, email);

        // 2. 验证文件
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("请选择要上传的文件");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("文件名为空");
        }

        // 3. 检测文件类型
        String fileType = FileTypeUtil.detectFileType(originalFilename);

        // 4. 校验文件大小
        long fileSize = file.getSize();
        FileTypeUtil.checkFileSize(fileSize, maxFileSize);

        // 5. 确保上传目录存在
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", uploadDir, e);
            throw new BadRequestException("文件上传失败，请联系管理员");
        }

        // 6. 保存文件到磁盘（文件名加时间戳防止冲突）
        String storedFilename = System.currentTimeMillis() + "_" + originalFilename;
        Path destPath = Paths.get(uploadDir, storedFilename);
        try {
            file.transferTo(destPath.toFile());
            log.info("文件已保存: {}", destPath);
        } catch (IOException e) {
            log.error("保存文件失败: {}", destPath, e);
            throw new BadRequestException("文件保存失败，请重试");
        }

        // 7. 创建文档数据库记录
        KbDocument document = new KbDocument(kbId, originalFilename, fileType, fileSize,
                destPath.toAbsolutePath().toString());
        documentMapper.insert(document);

        // 8. 异步触发文档处理
        ragProcessingService.processDocument(document.getId());

        log.info("用户 {} 上传文档: {} 到知识库 {} (id={})",
                email, originalFilename, kb.getName(), kbId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", document.getId());
        result.put("filename", document.getFilename());
        result.put("fileType", document.getFileType());
        result.put("fileSize", document.getFileSize());
        result.put("status", document.getStatus());
        result.put("createdAt", document.getCreatedAt());
        return result;
    }

    @Override
    public List<Map<String, Object>> listDocuments(String email, Long kbId) {
        // 验证知识库所有权
        findKnowledgeBase(kbId, email);

        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getKbId, kbId)
                .isNull(KbDocument::getDeletedAt)
                .orderByDesc(KbDocument::getCreatedAt);
        List<KbDocument> documents = documentMapper.selectList(wrapper);

        return documents.stream().map(doc -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", doc.getId());
            map.put("filename", doc.getFilename());
            map.put("fileType", doc.getFileType());
            map.put("fileSize", doc.getFileSize());
            map.put("status", doc.getStatus());
            map.put("errorMessage", doc.getErrorMessage());
            map.put("chunkCount", doc.getChunkCount());
            map.put("createdAt", doc.getCreatedAt());
            return map;
        }).collect(Collectors.toList());
    }

    @Override
    public void deleteDocument(String email, Long kbId, Long docId) {
        // 验证知识库所有权
        findKnowledgeBase(kbId, email);

        // 验证文档存在于该知识库中
        KbDocument doc = documentMapper.selectById(docId);
        if (doc == null || !doc.getKbId().equals(kbId)) {
            throw new NotFoundException("文档不存在");
        }
        if (doc.getDeletedAt() != null) {
            throw new BadRequestException("文档已被删除");
        }

        // 软删除文档
        LocalDateTime now = LocalDateTime.now();
        doc.setDeletedAt(now);
        documentMapper.updateById(doc);

        log.info("用户 {} 删除文档: {} (id={})", email, doc.getFilename(), docId);
    }

    // ==================== 检索查询 ====================

    @Override
    public List<Map<String, Object>> searchChunks(Long kbId, String query, int maxResults, double minScore) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        // 嵌入查询文本
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // 在向量库中搜索
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(
                EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(maxResults > 0 ? maxResults : maxRetrieveResults)
                        .minScore(minScore > 0 ? minScore : minRelevanceScore)
                        .build()
        );

        // 过滤：只保留属于指定知识库的块
        List<Map<String, Object>> chunks = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : result.matches()) {
            TextSegment segment = match.embedded();
            if (segment == null || segment.metadata() == null) continue;

            // 使用 metadata().toMap() 获取自定义字段
            Map<String, ?> metaMap = segment.metadata().toMap();
            String chunkKbId = (String) metaMap.get("kb_id");
            if (chunkKbId != null && String.valueOf(kbId).equals(chunkKbId)) {
                Map<String, Object> chunk = new HashMap<>();
                chunk.put("content", segment.text());
                chunk.put("filename", metaMap.get("filename"));
                chunk.put("chunkIndex", metaMap.get("chunk_index"));
                chunk.put("score", match.score());
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    @Override
    public String retrieveContext(Long kbId, String query, int maxResults, double minScore) {
        List<Map<String, Object>> chunks = searchChunks(kbId, query, maxResults, minScore);

        if (chunks.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        for (Map<String, Object> chunk : chunks) {
            String filename = (String) chunk.getOrDefault("filename", "未知文件");
            String chunkIndex = (String) chunk.getOrDefault("chunkIndex", "0");
            String content = (String) chunk.get("content");

            // 将块序号转换为人类可读的段号（从 0 开始 → 第 1 段）
            int segmentNum = 1;
            try {
                segmentNum = Integer.parseInt(chunkIndex) + 1;
            } catch (NumberFormatException ignored) {
            }

            context.append("[来源: ").append(filename)
                    .append(" (第").append(segmentNum).append("段)]\n")
                    .append(content).append("\n\n");
        }

        return context.toString().trim();
    }

    // ==================== 定期清理 ====================

    @Override
    public int cleanupOldDeletedRecords() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(30);
        int totalDeleted = 0;

        // 查找超过 30 天的软删除知识库
        LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
        kbWrapper.isNotNull(KnowledgeBase::getDeletedAt)
                .le(KnowledgeBase::getDeletedAt, threshold);
        List<KnowledgeBase> oldKbs = knowledgeBaseMapper.selectList(kbWrapper);

        for (KnowledgeBase kb : oldKbs) {
            // 物理删除文本块（通过文档关联）
            LambdaQueryWrapper<KbDocument> docWrapper = new LambdaQueryWrapper<>();
            docWrapper.eq(KbDocument::getKbId, kb.getId());
            List<KbDocument> docs = documentMapper.selectList(docWrapper);
            for (KbDocument doc : docs) {
                LambdaQueryWrapper<KbChunk> chunkWrapper = new LambdaQueryWrapper<>();
                chunkWrapper.eq(KbChunk::getDocumentId, doc.getId());
                chunkMapper.delete(chunkWrapper);
            }
            // 物理删除文档
            documentMapper.delete(docWrapper);
            // 物理删除知识库
            knowledgeBaseMapper.deleteById(kb.getId());
            totalDeleted++;
        }

        if (totalDeleted > 0) {
            log.info("定期清理：物理删除了 {} 个超过 30 天的软删除知识库", totalDeleted);
        }
        return totalDeleted;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 根据邮箱查找用户
     */
    private User findUserByEmail(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            throw new NotFoundException("用户不存在");
        }
        return user;
    }

    /**
     * 查找知识库并验证所有权
     */
    private KnowledgeBase findKnowledgeBase(Long kbId, String email) {
        User user = findUserByEmail(email);
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb == null || kb.getDeletedAt() != null) {
            throw new NotFoundException("知识库不存在");
        }
        if (!kb.getUserId().equals(user.getId())) {
            throw new ForbiddenException("无权访问此知识库");
        }
        return kb;
    }
}
