# Phase 2：RAG 知识库 （已完成）

## 概述

在 Phase 1 AI 智能对话的基础上，新增 **RAG（检索增强生成）** 能力。用户可以上传文档构建专属知识库，AI 回答时会自动检索知识库中的相关内容并注入到对话上下文中。

**核心能力**：文档上传 → Apache Tika 文本提取 → 递归分块 → OpenAI 兼容 Embedding → 余弦相似度检索 → System Prompt 注入 → SSE 流式对话。

### RAG vs 普通 AI 对话

|维度 |普通 AI 对话 |RAG 对话 |
|---|---|---|
|知识来源 |模型训练数据（截止 2024 年） |用户上传的专属文档 |
|回答准确度 |通用知识，可能幻觉 |基于文档原文，有据可查 |
|知识更新 |依赖模型重新训练 |上传新文档即可即时更新 |
|隐私性 |所有对话经 API 发送到模型 |文档在服务端检索，只注入相关块 |


---

## 技术选型与决策

### 为什么自实现 EmbeddingStore 而不引入 ChromaDB/Qdrant？

|方案 |优点 |缺点 |本阶段选择 |
|---|---|---|:---:|
|自实现内存向量库 |零依赖、零配置、开发快 |内存限制、重启丢失（需重建） |✅ Phase 2 |
|ChromaDB / Qdrant |持久化、高性能、支持过滤 |需要额外 Docker 容器、运维复杂 |Phase 3+ |
|Redis Stack |复用现有 Redis、支持索引 |需要 Redis Stack 版本（非标准 Redis） |备选 |

**设计考量**：langchain4j-core 1.15.0 中没有 `InMemoryEmbeddingStore`（该类仅在聚合 jar 中存在，Maven 模块结构导致无法直接引用）。为此自实现了 `SimpleEmbeddingStore`——实现 `EmbeddingStore<TextSegment>` 接口，使用 `CopyOnWriteArrayList` 存储（id, Embedding, TextSegment）三元组，检索时计算余弦相似度排序。

**数据持久化**：向量数据存储在内存中，但 MySQL `kb_chunk` 表持久化了所有文本块。应用启动时通过 `@PostConstruct` 从 MySQL 重建向量索引——这是一个实用的"无额外基础设施"策略。

### 为什么直接用 Apache Tika 而不用 LangChain4j 的文档解析器？

langchain4j 1.15.0 的标准 Maven 模块中不包含 `langchain4j-document-parser-apache-tika`（该模块在最新版中才稳定，且 Maven 仓库中存在但不兼容当前依赖树）。直接用 Apache Tika 反而更清晰——Tika 的 `AutoDetectParser` 自动识别 PDF/DOCX/DOC/TXT/MD 格式并提取文本，代码量相当。

### 为什么用自实现 TextSplitterUtil 而不用 LangChain4j 的 DocumentSplitters？

同理，`DocumentSplitters.recursive()` 在 langchain4j 1.15.0 的标准模块中不可用。自实现的递归分割器按「段落 → 句子 → 字符」的顺序尝试分割，与 LangChain 的 RecursiveCharacterTextSplitter 策略一致。


---

## Phase 2 构建全过程

### 第一步：新增 RAG 依赖 — Apache Tika

**操作**：在 [pom.xml](springboot/pom.xml) 中新增：

```xml
<!-- Apache Tika：文档文本提取 -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.2</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.2</version>
</dependency>
```

**设计要点**：

- `tika-core`：核心接口（`Parser`、`Metadata`、`ParseContext`），编译时依赖
- `tika-parsers-standard-package`：包含 PDF、DOCX、TXT 等常用格式的解析器实现。首次运行时 Tika 会扫描 classpath 加载解析器，耗时 ~1-2 秒
- **版本 2.9.2**：与 langchain4j 1.15.0 兼容的稳定版本
- **Embedding 模型**：`langchain4j-open-ai` 已在 Phase 1 引入，其中包含 `OpenAiEmbeddingModel`，无需额外依赖


---

### 第二步：数据库迁移 — 知识库三表

**操作**：创建 [V3__rag_tables.sql](springboot/src/main/resources/db/migration/V3__rag_tables.sql)。

```sql
-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    name        VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at  DATETIME DEFAULT NULL,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE,
    INDEX idx_kb_user (user_id, deleted_at)
);

-- 文档表
CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id        BIGINT NOT NULL,
    filename     VARCHAR(255) NOT NULL,
    file_type    VARCHAR(10) NOT NULL,
    file_size    BIGINT NOT NULL,
    file_path    VARCHAR(500) NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    chunk_count  INT DEFAULT 0,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted_at   DATETIME DEFAULT NULL,
    FOREIGN KEY (kb_id) REFERENCES knowledge_base(id) ON DELETE CASCADE,
    INDEX idx_doc_kb (kb_id, deleted_at)
);

-- 文本块表
CREATE TABLE IF NOT EXISTS kb_chunk (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id   BIGINT NOT NULL,
    chunk_index   INT NOT NULL,
    content       TEXT NOT NULL,
    char_count    INT NOT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES kb_document(id) ON DELETE CASCADE,
    INDEX idx_chunk_doc (document_id, chunk_index)
);
```

**设计要点**：

- **三层关联**：知识库 → 文档（1:N）→ 文本块（1:N），删知识库级联删文档和块
- `deleted_at` 软删除：知识库和文档使用软删除模式。`deleted_at IS NULL` 表示有效数据，设置时间戳表示已删除。定时任务（每天 3AM）物理清理超过 30 天的软删除记录
- **文档状态机**：`PENDING → PROCESSING → COMPLETED / FAILED`。上传后立即返回 PENDING，异步处理管道完成后更新状态
- `chunk_index`：文本块在文档中的序号，用于构建 RAG 上下文时标注来源段落号
- `content` 用 TEXT：单个文本块最多 800 字符，TEXT 类型（最大 65535）足够
- `error_message`：文档处理失败时记录错误原因（如"文件格式不支持"、"解析超时"）

**Docker 部署**：在 `docker-compose.yml` 中挂载 `V3__rag_tables.sql` 到 `/docker-entrypoint-initdb.d/03-rag-tables.sql`。


---

### 第三步：实体类 — KnowledgeBase + KbDocument + KbChunk

**操作**：创建三个实体类。

[**KnowledgeBase.java**](springboot/src/main/java/com/zora/entity/KnowledgeBase.java)：

```java
@TableName("knowledge_base")
public class KnowledgeBase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;  // 软删除：NULL=有效，非NULL=已删除
    // getter/setter + 构造器...
}
```

[**KbDocument.java**](springboot/src/main/java/com/zora/entity/KbDocument.java)：

```java
@TableName("kb_document")
public class KbDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long kbId;
    private String filename;
    private String fileType;   // pdf/docx/doc/txt/md
    private Long fileSize;
    private String filePath;   // 上传目录中的绝对/相对路径
    private String status;     // PENDING / PROCESSING / COMPLETED / FAILED
    private String errorMessage;
    private Integer chunkCount;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    // 状态常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";
}
```

[**KbChunk.java**](springboot/src/main/java/com/zora/entity/KbChunk.java)：

```java
@TableName("kb_chunk")
public class KbChunk {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private Integer chunkIndex;  // 该块在文档中的序号（从 1 开始）
    private String content;     // 文本块内容（≤800 字符）
    private Integer charCount;  // 实际字符数
    private LocalDateTime createdAt;
}
```

**设计要点**：

- **KbDocument 的状态常量**：定义为 `public static final String` 而非 `enum`——MyBatis-Plus 直接操作字符串更简单，且避免了枚举序列化问题。常量同时给 Service 和 Controller 使用
- **KbChunk 的** `chunkIndex`：从 1 开始递增，用于 RAG 检索结果中标注"（第N段）"
- `deletedAt` 字段无需 `@TableLogic`：MyBatis-Plus 的 `@TableLogic` 会自动注入 `deleted_at IS NULL` 条件，但本项目手动控制 `LambdaQueryWrapper` 中的查询条件——更灵活（如回收站场景需要查已删除的记录）


---

### 第四步：Mapper 接口 + 工具类

**操作**：创建三个 Mapper + 两个工具类。

**Mapper**（继承 `BaseMapper<T>`，零 XML）：

- `KnowledgeBaseMapper.java` — `extends BaseMapper<KnowledgeBase>`
- `KbDocumentMapper.java` — `extends BaseMapper<KbDocument>`
- `KbChunkMapper.java` — `extends BaseMapper<KbChunk>`

[**FileTypeUtil.java**](springboot/src/main/java/com/zora/utils/FileTypeUtil.java)：

```java
public class FileTypeUtil {
    // 支持的文件类型
    private static final Set<String> ALLOWED_EXTENSIONS =
        Set.of("pdf", "docx", "doc", "txt", "md");

    // 最大文件大小（10MB）
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    /** 从文件名检测文件类型 */
    public static String detectFileType(String filename) {
        String ext = getExtension(filename);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BadRequestException("不支持的文件类型：" + ext);
        }
        return ext;
    }

    /** 校验文件大小 */
    public static void checkFileSize(long size, long maxSize) {
        if (size > maxSize) {
            throw new BadRequestException("文件大小超过限制（最大 10MB）");
        }
    }
}
```

[**TextSplitterUtil.java**](springboot/src/main/java/com/zora/utils/TextSplitterUtil.java)：

```java
public class TextSplitterUtil {
    /**
     * 递归文本分割
     * @param text 原始文本
     * @param chunkSize 每块最大字符数（默认 800）
     * @param overlap 块间重叠字符数（默认 100）
     * @return 分割后的文本块列表
     */
    public static List<String> split(String text, int chunkSize, int overlap);
}
```

**设计要点**：

- **分割策略**：先按段落（`\n\n`）分割 → 如果单段超限，按句子（`。！？\n`）分割 → 如果单句仍超限，按 `chunkSize` 硬切
- **overlap 机制**：块间重叠 100 字符。当检索命中块边缘的关键信息时，重叠确保相邻块也能被检索到——提高召回率
- **为什么是递归分割**：这是 LangChain 的经典策略。先尝试粗粒度的语义分割（段落），对超长段落逐步细化到句子和字符。这样以块的语义完整性最高


---

### 第五步：RAG 配置类 — EmbeddingModel + EmbeddingStore Bean

**操作**：创建 [RagConfig.java](springboot/src/main/java/com/zora/config/RagConfig.java)。

```java
@Configuration
public class RagConfig {
    @Value("${rag.embedding.api-key}")
    private String embeddingApiKey;
    @Value("${rag.embedding.base-url:https://api.openai.com/v1}")
    private String embeddingBaseUrl;
    @Value("${rag.embedding.model-name:text-embedding-3-small}")
    private String embeddingModelName;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .baseUrl(embeddingBaseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(false)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new SimpleEmbeddingStore();
    }
}
```

**设计要点**：

- `OpenAiEmbeddingModel` 复用 OpenAI 兼容适配器：与 `OpenAiStreamingChatModel` 同理——只用改 `baseUrl` 就能切换到硅基流动等国内 Embedding 提供商
- `logResponses: false`：Embedding 响应体包含长度为 1536 的浮点向量数组，打印到日志既无用又占空间
- `timeout: 60s`：单次 embedding 请求通常 1-2 秒完成，60 秒给足网络波动余量
- **Embedding 模型推荐**：`text-embedding-3-small`（OpenAI）或 `bge-large-zh-v1.5`（硅基流动，中文优化）

**配置文件更新**：在 [application.yml](springboot/src/main/resources/application.yml) 中添加：

```yaml
rag:
  embedding:
    api-key: ${AI_EMBEDDING_API_KEY:}
    base-url: ${AI_EMBEDDING_BASE_URL:https://api.openai.com/v1}
    model-name: ${AI_EMBEDDING_MODEL:text-embedding-3-small}
  document:
    max-size: 10485760           # 10MB
    upload-dir: ${RAG_UPLOAD_DIR:./uploads/rag}
    max-chunk-size: 800
    max-chunk-overlap: 100
    max-retrieve-results: 5
    min-relevance-score: 0.3
```


---

### 第六步：自实现向量存储 — SimpleEmbeddingStore

**操作**：创建 [SimpleEmbeddingStore.java](springboot/src/main/java/com/zora/service/impl/SimpleEmbeddingStore.java)，实现 `EmbeddingStore<TextSegment>` 接口。

```java
public class SimpleEmbeddingStore implements EmbeddingStore<TextSegment> {
    // 存储三元组：id + Embedding + TextSegment
    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        String id = UUID.randomUUID().toString();
        entries.add(new Entry(id, embedding, segment));
        return id;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(
            EmbeddingSearchRequest request) {
        Embedding queryEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();

        return entries.stream()
            .map(entry -> {
                double score = cosineSimilarity(queryEmbedding, entry.embedding);
                return new EmbeddingMatch<>(score, entry.id, entry.embedding, entry.segment);
            })
            .filter(match -> match.score() >= minScore)
            .sorted((a, b) -> Double.compare(b.score(), a.score()))
            .limit(maxResults)
            .collect(Collectors.toList());
    }

    // 余弦相似度：dot(a, b) / (norm(a) * norm(b))
    private double cosineSimilarity(Embedding a, Embedding b) {
        float[] va = a.vector(), vb = b.vector();
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < va.length; i++) {
            dot += va[i] * vb[i];
            normA += va[i] * va[i];
            normB += vb[i] * vb[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

**设计要点**：

- `CopyOnWriteArrayList`：读（搜索）远多于写（添加块），COW 保证读无锁，修改时复制整个列表。对于百 MB 级别的知识库完全足够
- **余弦相似度**：最常用的文本向量相似度度量，值域 [-1, 1]。`minRelevanceScore: 0.3` 过滤掉弱相关结果
- **接口契约**：实现了标准的 `EmbeddingStore` 接口，日后可以无痛切换到 ChromaDB / Qdrant 等持久化向量库——只需改 Bean 注入
- **内存占用估算**：每个 1536 维 float 向量 ≈ 6KB。10 个文档 × 50 块/文档 = 500 块 → ~3MB 内存。即使 1000 个文档也只需 ~300MB，远低于 JVM 限制


---

### 第七步：文档处理服务 — RagProcessingService

**操作**：创建 [RagProcessingServiceImpl.java](springboot/src/main/java/com/zora/service/impl/RagProcessingServiceImpl.java)。

这是整个 RAG 系统的**引擎**，负责将原始文档转化为可检索的向量块。

```java
@Service
public class RagProcessingServiceImpl implements RagProcessingService {

    @Async  // 异步执行，不阻塞上传接口
    public void processDocument(Long documentId) {
        // 1. 更新状态为 PROCESSING
        updateStatus(documentId, STATUS_PROCESSING);

        try {
            // 2. 用 Apache Tika 提取文本
            String text = extractText(document);

            // 3. 递归分块（800 字符 / 100 重叠）
            List<String> chunks = TextSplitterUtil.split(
                text, maxChunkSize, maxChunkOverlap);

            // 4. 逐块 → embed → 存入向量库 + 写入 kb_chunk 表
            for (int i = 0; i < chunks.size(); i++) {
                // 4a. 调用 Embedding API
                Embedding embedding = embeddingModel.embed(chunks.get(i))
                    .content();
                // 4b. 存入内存向量库（带文档元信息）
                embeddingStore.add(embedding, TextSegment.from(
                    chunks.get(i), Metadata.from("kb_id", kbId,
                        "filename", filename, "chunk_index", i + 1));
                // 4c. 持久化到数据库
                saveChunk(documentId, i + 1, chunks.get(i));
            }

            // 5. 标记为 COMPLETED
            updateStatus(documentId, STATUS_COMPLETED, chunks.size());

        } catch (Exception e) {
            // 6. 失败 → 标记 FAILED + 记录错误信息
            updateStatus(documentId, STATUS_FAILED, e.getMessage());
        }
    }

    /** 启动时从 MySQL 重建向量索引 */
    @PostConstruct
    public void rebuildEmbeddingStore() {
        List<KbDocument> docs = loadCompletedDocuments();
        for (KbDocument doc : docs) {
            List<KbChunk> chunks = loadChunks(doc.getId());
            for (KbChunk chunk : chunks) {
                Embedding embedding = embeddingModel.embed(chunk.getContent())
                    .content();
                embeddingStore.add(embedding, TextSegment.from(
                    chunk.getContent(),
                    Metadata.from("kb_id", doc.getKbId(),
                        "filename", doc.getFilename(), ...)));
            }
        }
    }
}
```

**设计要点**：

- `@Async` 异步处理：文档上传后立即返回 "PENDING" 状态，处理管道在后台异步执行。`AppStart` 已添加 `@EnableAsync` 启用异步支持
- **文本提取（Apache Tika）**：`AutoDetectParser` 自动识别文件类型 → `BodyContentHandler` 获取纯文本 → 处理上限 10MB 的 PDF 约需 3-5 秒
- `@PostConstruct` 启动重建：应用重启后向量数据全部丢失（在内存中），此方法从 MySQL `kb_chunk` 表加载所有 `COMPLETED` 状态的文档块，逐一重新 embedding 并存入向量库。对 500 个块重建约需 10-20 秒
- **Metadata 携带来源信息**：每个 TextSegment 附加 `kb_id`、`filename`、`chunk_index`，检索时可用于过滤和来源标注
- **为什么先存向量库再写数据库？** 如果先写数据库再 embed 失败，数据库中会有不可检索的孤立向量。先 embed → 验证成功 → 再写数据库，保证数据一致性


---

### 第八步：知识库服务 — RagService

**操作**：创建 [RagServiceImpl.java](springboot/src/main/java/com/zora/service/impl/RagServiceImpl.java)，约 790 行（含回收站功能）。

核心功能：

#### 22.1 知识库 CRUD

```java
// 创建知识库
public Map<String, Object> createKnowledgeBase(String email, String name, String desc) {
    // 校验名称非空 + 不超过 200 字符
    // 插入 KnowledgeBase 记录 → 返回 id + name + description
}

// 列出用户的知识库（含文档数）
public List<Map<String, Object>> listKnowledgeBases(String email) {
    // 查用户的所有未删除知识库 → 对每个 KB 统计文档数 → 返回列表
}

// 获取/更新/删除（含所有权校验）
// getKnowledgeBase → 验证 userId 匹配 → 查文档列表 → 返回完整信息
// updateKnowledgeBase → 验证所有权 → 更新 name/description
// deleteKnowledgeBase → 验证所有权 → 设置 deleted_at（软删除）
```

**所有权校验模式**：

```java
KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
if (!kb.getUserId().equals(currentUserId)) {
    throw new ForbiddenException("无权访问此知识库");
}
```

这是一个关键的**安全模式**——每个操作都校验"操作者是否拥有此资源"。不校验的话，用户 A 可以通过猜 ID 修改用户 B 的知识库。

#### 22.2 文档上传

```java
public Map<String, Object> uploadDocument(String email, Long kbId, MultipartFile file) {
    // 1. 验证知识库所有权
    // 2. FileTypeUtil 检测文件类型 + 校验大小（max 10MB）
    // 3. 保存到磁盘（uploadDir / timestamp_filename）
    // 4. 创建 KbDocument 记录（status = PENDING）
    // 5. 异步触发 ragProcessingService.processDocument(docId)
    // 6. 立即返回上传成功（处理在后台进行）
}
```

**关键设计**：

- **立即返回 PENDING**：大文档的解析+embedding 可能需要 10-30 秒，HTTP 请求不能等这么久。返回 PENDING 后前端轮询状态
- **文件名防冲突**：保存为 `{timestamp}_{original_filename}`，避免同名文件覆盖
- **错误隔离**：处理失败只影响单个文档（标记 FAILED），不影响知识库中其他文档

#### 22.3 检索与上下文构建

```java
// 向量检索
public List<Map<String, Object>> searchChunks(Long kbId, String query,
                                                int maxResults, double minScore) {
    if (query == null || query.trim().isEmpty()) return Collections.emptyList();
    // 1. embed 查询文本
    Embedding queryEmbedding = embeddingModel.embed(query).content();
    // 2. 在向量库中搜索
    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
        .queryEmbedding(queryEmbedding)
        .maxResults(maxResults * 3)  // 多搜一些，后续按 kb_id 过滤
        .minScore(minScore)
        .build();
    // 3. 过滤出属于该知识库的块 → 按分数排序 → 截取 maxResults
    // 4. 返回 [{content, filename, chunkIndex, score}, ...]
}

// RAG 上下文构建
public String retrieveContext(Long kbId, String query,
                               int maxResults, double minScore) {
    List<Map<String, Object>> chunks = searchChunks(kbId, query, ...);
    if (chunks.isEmpty()) return "";
    StringBuilder sb = new StringBuilder("【知识库参考内容（请优先根据以下内容回答用户问题）】\n");
    for (Map<String, Object> chunk : chunks) {
        sb.append(String.format("[来源: %s (第%d段)]\n%s\n\n",
            chunk.get("filename"), chunk.get("chunkIndex"), chunk.get("content")));
    }
    sb.append("【知识库内容结束】\n");
    return sb.toString();
}
```

**检索策略**：

- **先多搜再过滤**：`searchChunks` 请求 `maxResults * 3` 条结果，然后过滤出属于指定 `kb_id` 的块。这是因为向量库不区分知识库——一个用户的多个知识库混在一起，需要在 Java 层面过滤
- `minScore: 0.3`：余弦相似度阈值。0.3 以下的结果与查询几乎无关，过滤掉减少噪音
- **来源标注**：RAG 上下文中标注文件名和段落号，帮助 AI 定位信息来源（也让用户知道回答的依据）

#### 22.4 两级回收站

知识库和文档都支持软删除（`deleted_at` 字段），删除后进入回收站，30 天内可恢复。恢复时自动重新嵌入向量；永久删除时清理磁盘文件、向量、数据库记录。

**知识库回收站**（全局）：

```java
// 列出用户已删除的知识库
public List<Map<String, Object>> listDeletedKnowledgeBases(String email);

// 恢复知识库 + 旗下所有文档（重新嵌入向量）
public void restoreKnowledgeBase(String email, Long kbId);

// 永久删除：磁盘文件 → 向量 → chunks → documents → knowledge_base
public void permanentlyDeleteKnowledgeBase(String email, Long kbId);
```

**文档回收站**（按知识库）：

```java
// 列出指定知识库中已删除的文档
public List<Map<String, Object>> listDeletedDocuments(String email, Long kbId);

// 恢复单个文档（若父 KB 也已删除，自动恢复 KB）
public void restoreDocument(String email, Long docId);

// 永久删除单个文档
public void permanentlyDeleteDocument(String email, Long docId);

// 清空指定知识库的文档回收站
public int emptyDocumentRecycleBin(String email, Long kbId);
```

**关键设计**：

- **恢复时重新嵌入**：`restoreDocument` 恢复文档后，从 `kb_chunk` 表加载所有文本块，重新调用 `EmbeddingModel.embed()` 存入 `SimpleEmbeddingStore`。因为应用重启时 `rebuildEmbeddingStore` 会排除已删除文档，所以恢复必须重新嵌入
- **MyBatis-Plus null 字段陷阱**：`updateById()` 默认使用 `NOT_NULL` 策略，`setDeletedAt(null)` 后调用 `updateById` 实际上不会把 `deleted_at` 设为 NULL。必须使用 `UpdateWrapper.set("deleted_at", null)` 显式设置
- **永久删除顺序**：先删文件（非关键，失败不阻止后续）→ 移除向量 → 删 chunks → 删文档记录。不可逆操作放最后


---

### 第九步：RAG 流式对话 — AiChatService 扩展

**操作**：修改 [AiChatService.java](springboot/src/main/java/com/zora/service/AiChatService.java) 和 [AiChatServiceImpl.java](springboot/src/main/java/com/zora/service/impl/AiChatServiceImpl.java)。

#### 23.1 接口新增方法

```java
Flux<String> streamChatWithRag(String email, String userMessage,
                                Long conversationId, Long knowledgeBaseId);
```

#### 23.2 RAG 对话实现

```java
@Override
public Flux<String> streamChatWithRag(String email, String userMessage,
                                        Long conversationId, Long knowledgeBaseId) {
    // 1. 获取或创建会话（复用现有逻辑）
    Long convId = getOrCreateConversation(email, conversationId);

    // 2. 保存用户消息
    saveMessage(convId, "user", userMessage);

    // 3. 检索 RAG 上下文
    String ragContext = ragService.retrieveContext(knowledgeBaseId, userMessage, 5, 0.3);

    // 4. 构建增强 System Prompt
    List<dev.langchain4j.data.message.ChatMessage> messages = ragContext.isEmpty()
        ? buildMessages(convId, userMessage)          // 无结果 → 降级普通对话
        : buildMessagesWithRag(convId, userMessage, ragContext);  // 注入上下文

    // 5. 流式输出（与普通对话相同的 Flux.create 逻辑）
    // ...
}
```

#### 23.3 RAG System Prompt 构建

```java
private String buildRagSystemPrompt(String ragContext) {
    return ragContext + "\n" + SYSTEM_PROMPT;
    // 效果：
    // 【知识库参考内容（请优先根据以下内容回答用户问题）】
    // [来源: spring-boot-guide.pdf (第3段)] Spring Boot is a framework...
    // [来源: spring-boot-guide.pdf (第7段)] Auto-configuration...
    // 【知识库内容结束】
    //
    // 你是一个有帮助的 AI 助手。请用中文回答问题。
    // 【安全规则】...
}
```

**设计要点**：

- **上下文前置**：RAG 上下文放在 System Prompt 的**最前面**，遵循"最重要信息最先看到"的原则。大模型倾向于给 prompt 开头和结尾的内容最高权重
- **优雅降级**：检索无结果时（`ragContext.isEmpty()`），自动退化为 `buildMessages()` 普通对话。前端无需感知——用户体验是"AI 回答了，但可能没用上知识库"
- **知识库隔离**：每个用户只能用自己的知识库，`retrieveContext` 内部通过 `kb_id` 过滤


---

### 第十步：RAG Controller + 配置更新

**操作**：创建 [RagController.java](springboot/src/main/java/com/zora/controller/RagController.java)，16 个 REST 端点（含两级回收站）。

```java
@RestController
@RequestMapping("/rag")
public class RagController {
    @Resource
    private RagService ragService;

    // --- 知识库 CRUD（5 个端点）---
    @PostMapping("/knowledge-bases")        // 创建
    @GetMapping("/knowledge-bases")         // 列出
    @GetMapping("/knowledge-bases/{id}")    // 详情
    @PutMapping("/knowledge-bases/{id}")    // 更新
    @DeleteMapping("/knowledge-bases/{id}") // 软删除

    // --- 文档管理（3 个端点）---
    @PostMapping("/knowledge-bases/{id}/documents")               // 上传(multipart)
    @GetMapping("/knowledge-bases/{id}/documents")                // 列出
    @DeleteMapping("/knowledge-bases/{id}/documents/{docId}")     // 删除

    // --- 检索测试（1 个端点）---
    @PostMapping("/knowledge-bases/{id}/query")  // 检索相关块
}
```

**认证方式**：所有 `/rag/**` 接口都需要登录。`WebConfig` 的拦截器 `addPathPatterns("/**")` 已自动覆盖，无需单独配置。

**修改 AiChatController**：新增 RAG 流式对话端点：

```java
@PostMapping(value = "/chat/rag-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<String> ragStreamChat(@RequestBody Map<String, Object> body, HttpServletRequest request) {
    String email = (String) request.getAttribute("userEmail");
    String message = (String) body.get("message");
    Long conversationId = parseLong(body.get("conversationId"));
    Long knowledgeBaseId = parseLong(body.get("knowledgeBaseId"));
    return chatService.streamChatWithRag(email, message, conversationId, knowledgeBaseId);
}
```


---

### 第十一步：定时清理 + 配置外部化

**操作**：修改 [CleanupTask.java](springboot/src/main/java/com/zora/config/CleanupTask.java)。

```java
// 每天凌晨 3:00 执行
@Scheduled(cron = "0 0 3 * * ?")
public void cleanup() {
    // ... 原有清理逻辑 ...

    // 新增：物理删除超过 30 天的软删除知识库/文档/块
    ragService.cleanupOldDeletedRecords();
}
```

**Docker / .env 更新**：新增 Embedding API 环境变量（已在 Step 2 中说明）。


---

### 第十二步：前端 RAG API 封装

**操作**：创建 [rag.js](web/frontend/src/api/rag.js)。

```js
import request from './index'
import { getToken } from '@/utils/token'

// 知识库 CRUD
export const createKnowledgeBase = (name, description) =>
  request.post('/rag/knowledge-bases', { name, description })
export const listKnowledgeBases = () =>
  request.get('/rag/knowledge-bases')
export const updateKnowledgeBase = (id, name, description) =>
  request.put(`/rag/knowledge-bases/${id}`, { name, description })
export const deleteKnowledgeBase = (id) =>
  request.delete(`/rag/knowledge-bases/${id}`)

// 文档管理
export const uploadDocument = (kbId, file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/rag/knowledge-bases/${kbId}/documents`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
export const listDocuments = (kbId) =>
  request.get(`/rag/knowledge-bases/${kbId}/documents`)
export const deleteDocument = (kbId, docId) =>
  request.delete(`/rag/knowledge-bases/${kbId}/documents/${docId}`)

// 检索测试
export const queryKnowledgeBase = (kbId, query, maxResults = 5, minScore = 0.3) =>
  request.post(`/rag/knowledge-bases/${kbId}/query`, { query, maxResults, minScore })

// SSE RAG 流式对话（原生 fetch，复用 Phase 1 的流式读取逻辑）
export const streamRagChat = (message, conversationId, knowledgeBaseId,
                               onToken, onDone, onError) => {
  // 与 streamChat 相同的实现，body 中多传 knowledgeBaseId
}
```


---

### 第十三步：前端知识库管理页面

**操作**：创建 [KnowledgeBase.vue](web/frontend/src/views/KnowledgeBase.vue)，约 780 行（含两级回收站）。

核心功能：

|功能区域 |实现 |
|---|---|
|卡片列表 |Element Plus 卡片 + 展开/收起，显示文档数和更新时间 |
|创建/编辑对话框 |`el-dialog` + 表单，名称 200 字限制 |
|文档上传 |`el-upload`，限制 PDF/DOCX/DOC/TXT/MD，最大 10MB |
|文档表格 |Element Plus `el-table`，文件名/类型/大小/状态/块数/时间/删除 |
|状态轮询 |`setInterval` 每 5 秒检测有 PENDING/PROCESSING 文档时自动刷新 |
|检索测试面板 |输入查询 → 列出相关块 + 来源文件 + 相关度分数 |
|空状态 |`el-empty` + 引导按钮 |

**Chat.vue 修改**：

- 聊天头部新增 RAG 开关 `el-switch` + 知识库选择器 `el-select`
- `handleSend()` 中：RAG 开启时调用 `streamRagChat()`，关闭时调用 `streamChat()`
- 输入区底部显示 "RAG 已启用 · 知识库：XX" 指示器
- 侧边栏底部新增 "知识库" 导航按钮 → `router.push('/knowledge')`

**路由新增**：

```js
{
  path: '/knowledge',
  name: 'Knowledge',
  component: () => import('@/views/KnowledgeBase.vue'),
  meta: { requiresAuth: true },
}
```


---

### 第十四步：后端 RAG 测试用例

**操作**：创建两个测试类。

[**RagServiceImplTest.java**](springboot/src/test/java/com/zora/service/RagServiceImplTest.java) — 21 个测试（含回收站）：

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("RagServiceImpl 服务测试")
class RagServiceImplTest {
    @Mock private UserMapper userMapper;
    @Mock private KnowledgeBaseMapper knowledgeBaseMapper;
    @Mock private KbDocumentMapper documentMapper;
    @Mock private RunnableProcessingService ragProcessingService;
    @Mock private EmbeddingModel embeddingModel;
    @Mock private SimpleEmbeddingStore embeddingStore;
    @InjectMocks private RagServiceImpl ragService;

    @Nested @DisplayName("知识库 CRUD")
    class KnowledgeBaseCrud {
        @Test @DisplayName("创建知识库：正常输入 → 返回 id+name")
        @Test @DisplayName("创建知识库：名称为空 → BadRequestException")
        @Test @DisplayName("访问他人知识库 → ForbiddenException")
        @Test @DisplayName("访问不存在的知识库 → NotFoundException")
        // ...
    }

    @Nested @DisplayName("文档管理")
    class DocumentManagement { ... }

    @Nested @DisplayName("检索查询")
    class SearchTests {
        @Test @DisplayName("空查询：返回空列表")
        @Test @DisplayName("retrieveContext：无结果时返回空字符串")
    }
}
```

[**RagControllerTest.java**](springboot/src/test/java/com/zora/controller/RagControllerTest.java) — 15 个测试（含回收站）：

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("RagController 控制器测试")
class RagControllerTest {
    private MockMvc mockMvc;
    @Mock private RagService ragService;

    @Nested @DisplayName("知识库 CRUD")  // 5 个测试
    @Nested @DisplayName("文档管理")      // 3 个测试
    @Nested @DisplayName("检索查询")      // 1 个测试
}
```

**运行结果**：

```bash
cd springboot && mvn test
# Tests: 212, Failures: 0, Errors: 0, Skipped: 0
```


---

## Phase 2 数据流全景图

```text
用户上传文档（KnowledgeBase.vue）
  → POST /rag/knowledge-bases/{id}/documents (multipart)
    → RagController.uploadDocument()
      → RagService.uploadDocument()
        ├─ file.saveToDisk(uploadDir/timestamp_filename)
        ├─ KbDocument(status=PENDING) → INSERT
        └─ @Async RagProcessingService.processDocument(docId)
              │
              ├─ updateStatus(PROCESSING)
              ├─ Apache Tika AutoDetectParser → 纯文本
              ├─ TextSplitterUtil.split(text, 800, 100)
              ├─ for each chunk:
              │   ├─ EmbeddingModel.embed(chunk) → float[1536]
              │   ├─ SimpleEmbeddingStore.add(embedding, TextSegment)
              │   └─ KbChunk(content, chunkIndex) → INSERT
              └─ updateStatus(COMPLETED, chunk_count)

前端轮询（每 5s）→ 检测 COMPLETED → 显示 "已完成" + 块数

═══════════════════════════════════════════════

RAG 对话（Chat.vue + RAG 开关启用）
  → POST /ai/chat/rag-stream {message, conversationId, knowledgeBaseId}
    → AiChatController.ragStreamChat()
      → AiChatService.streamChatWithRag()
        ├─ embed(query) → float[1536]
        ├─ SimpleEmbeddingStore.search(kb_id过滤, top-5, minScore: 0.3)
        ├─ 构建 RAG System Prompt:
        │   【知识库参考内容】
        │   [来源: readme.pdf (第3段)] ...内容...
        │   【知识库内容结束】
        │   {原有 System Prompt + 安全规则}
        ├─ Flux.create() + StreamingChatResponseHandler
        │   ├─ onPartialResponse(token) → sink.next(token)
        │   └─ onCompleteResponse() → saveMessage + sink.complete()
        └─ SSE → 前端逐字渲染

应用重启
  → RagProcessingService.@PostConstruct rebuildEmbeddingStore()
    → 查 MySQL (status=COMPLETED 且未删除)
      → 逐块 embed + rebuild SimpleEmbeddingStore
      → ~10-20s 完成 (500 块规模)
```


---

## Phase 2 文件变更总览

### 新增文件（14 个后端 + 2 个前端 + 1 测试）

|文件 |用途 |
|---|---|
|`springboot/.../config/RagConfig.java` |EmbeddingModel + EmbeddingStore Bean |
|`springboot/.../entity/KnowledgeBase.java` |知识库实体 |
|`springboot/.../entity/KbDocument.java` |文档实体（含状态常量） |
|`springboot/.../entity/KbChunk.java` |文本块实体 |
|`springboot/.../mapper/KnowledgeBaseMapper.java` |知识库 Mapper |
|`springboot/.../mapper/KbDocumentMapper.java` |文档 Mapper |
|`springboot/.../mapper/KbChunkMapper.java` |文本块 Mapper |
|`springboot/.../utils/FileTypeUtil.java` |文件类型检测 + 大小校验 |
|`springboot/.../utils/TextSplitterUtil.java` |递归文本分割器 |
|`springboot/.../service/RagService.java` |RAG 服务接口 |
|`springboot/.../service/RagProcessingService.java` |文档处理服务接口 |
|`springboot/.../service/impl/RagServiceImpl.java` |RAG 服务实现（~790 行） |
|`springboot/.../service/impl/RagProcessingServiceImpl.java` |文档处理实现（~210 行） |
|`springboot/.../service/impl/SimpleEmbeddingStore.java` |余弦相似度向量存储 |
|`springboot/.../controller/RagController.java` |REST API 控制器（16 端点） |
|`springboot/.../db/migration/V3__rag_tables.sql` |数据库迁移 SQL |
|`web/frontend/src/api/rag.js` |前端 RAG API 封装 |
|`web/frontend/src/views/KnowledgeBase.vue` |知识库管理页面 + 两级回收站（~780 行） |
|`springboot/src/test/.../service/RagServiceImplTest.java` |服务层测试（21 个） |
|`springboot/src/test/.../controller/RagControllerTest.java` |控制器测试（15 个） |

### 修改文件（8 个）

|文件 |变更 |
|---|---|
|`springboot/pom.xml` |新增 Apache Tika 依赖 |
|`springboot/.../AppStart.java` |新增 `@EnableAsync` |
|`springboot/.../config/CleanupTask.java` |新增 RAG 知识库定时清理 |
|`springboot/.../service/AiChatService.java` |新增 `streamChatWithRag()` 方法签名 |
|`springboot/.../service/impl/AiChatServiceImpl.java` |新增 RAG 流式对话实现 + buildMessagesWithRag |
|`springboot/.../controller/AiChatController.java` |新增 `POST /ai/chat/rag-stream` 端点 |
|`springboot/src/main/resources/application.yml` |新增 `rag:` 配置段 + springdoc 路径追加 `/rag/**` |
|`docker-compose.yml` |新增 V3 SQL 挂载 + Embedding 环境变量 + 上传卷 |
|`.env` |新增 Embedding API 配置 |
|`web/frontend/src/views/Chat.vue` |RAG 开关 + 知识库选择器 + RAG 聊天 + 导航按钮 |
|`web/frontend/src/router/index.js` |新增 `/knowledge` 路由 |


---

## 启动与测试

### RAG 功能验证清单

|# |测试场景 |预期结果 |
|---|---|---|
|1 |登录后进入"知识库管理" |显示知识库列表（初始为空） |
|2 |创建知识库"测试知识库" |卡片列表新增一条 |
|3 |上传 PDF 文档 |文件上传成功，状态 PENDING → PROCESSING → COMPLETED |
|4 |在检索面板输入关键词 |返回相关文本块，含相关度分数和来源文件名 |
|5 |进入 AI 对话，开启 RAG |选择知识库后，输入相关问题 |
|6 |RAG 对话：问知识库相关的问题 |AI 回答中引用了文档内容 |
|7 |RAG 对话：问知识库无关的问题 |AI 从通用知识回答（优雅降级） |
|8 |删除文档 |确认删除后，检索不再返回该文档的块 |
|9 |重启后端服务 |向量索引自动重建（检索仍能正常工作） |
|10 |关闭 RAG，问同样的问题 |普通聊天模式正常 |


---


