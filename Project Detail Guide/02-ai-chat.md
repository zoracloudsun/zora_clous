# AI 智能对话构建教程 — DeepSeek 智能对话（Phase 1）

## 概述

本教程记录在已有的 Spring Boot + Vue3 用户认证系统基础上，**新增 AI 智能对话功能（Phase 1）**、**RAG 知识库功能（Phase 2）**、**AI Agent 智能体（Phase 3）** 和 **智能搜索与分析（Phase 4）** 的完整构建过程。

- **AI 框架**：LangChain4j 1.15.0（Java 原生 AI 框架，对标 Python 的 LangChain）
- **大模型**：DeepSeek Chat（兼容 OpenAI API 格式，国内访问无需翻墙）
- **RAG 嵌入**：OpenAI 兼容 Embedding API + 自实现内存向量库 + 余弦相似度检索
- **文档处理**：Apache Tika 2.9.2（PDF/DOCX/DOC/TXT/MD 文本提取）+ 自实现递归分割器
- **前端渲染**：marked + highlight.js + DOMPurify（完整 Markdown + 代码高亮 + XSS 防护）
- **流式传输**：SSE（Server-Sent Events）逐字输出，打字机效果
- **全文搜索**：MySQL 8 InnoDB FULLTEXT INDEX + ngram parser 中文分词
- **数据可视化**：ECharts 6 图表库（折线图/柱状图/饼图可复用组件）
- **行为追踪**：Spring AOP + @TrackAction 注解 + @Async 异步日志写入

**核心能力**：用户登录后可与 DeepSeek AI 实时对话，支持多轮上下文、对话历史管理、Markdown 代码高亮渲染；可上传文档构建专属知识库，让 AI 回答基于用户自己的文档内容；Agent 模式支持 AI 自主调用工具（搜索/计算/代码执行）完成复杂任务；全文搜索跨对话检索历史消息；数据仪表盘可视化展示使用统计；基于行为的智能推荐。


---

## 技术选型与决策

### 为什么选 LangChain4j 而不是 Spring AI？

|维度 |LangChain4j |Spring AI |
|---|---|---|
|成熟度 |1.x 正式版，社区活跃 |1.0 GA 较新，生态建设中 |
|模型支持 |50+ 模型提供商（OpenAI、DeepSeek、Ollama 等） |类似，但部分集成较新 |
|核心能力 |Tool Calling、RAG、Agent、Memory 均内置 |类似，但 RAG 实现较简 |
|Java 原生 |纯 Java，无 Python 依赖 |同样纯 Java |
|与 Spring Boot 集成 |需手动配置 Bean |原生 Starter，自动配置 |

**结论**：LangChain4j 功能更全面，RAG 和 Agent 支持更成熟，且已有 LangGraph4j（对标 LangGraph）用于多 Agent 编排。本项目选用 LangChain4j。

### 为什么用 DeepSeek 而不是 OpenAI？

|维度 |DeepSeek |OpenAI |
|---|---|---|
|国内访问 |直连 `api.deepseek.com`，无需代理 |需要翻墙 |
|API 兼容 |完全兼容 OpenAI API 格式 |原生 |
|价格 |deepseek-chat ¥1/百万 token |GPT-4o $2.5/百万 token |
|性力 |DeepSeek-V4 综合能力接近 GPT-4o |GPT-4o 顶级 |
|免费额度 |注册送 500 万 token |无 |

**结论**：DeepSeek 是国内开发者的最优选择——价格低、直连、兼容 OpenAI 格式，LangChain4j 的 `OpenAiStreamingChatModel` 直接可用。

### 为什么用 SSE 而不是 WebSocket？

|维度 |SSE |WebSocket |
|---|---|---|
|方向 |服务端 → 客户端单向推送 |双向通信 |
|协议 |基于 HTTP，无需升级协议 |需要 HTTP Upgrade |
|实现复杂度 |Spring WebFlux `Flux<String>` 即可 |需要 STOMP 或自定义帧 |
|浏览器支持 |原生 `fetch` + ReadableStream |原生 WebSocket |
|断线重连 |浏览器自动重连 |需手动实现 |
|代理/CDN |兼容性好（标准 HTTP） |部分代理不支持 Upgrade |

**结论**：AI 对话是典型的"服务端推、客户端收"单向流场景，SSE 比 WebSocket 更轻量、更兼容。


---

## 项目构建全过程

### 第一步：添加后端依赖 — LangChain4j + WebFlux

**操作**：在 [pom.xml](springboot/pom.xml) 中新增两个依赖：

```xml
<!-- WebFlux：SSE 流式响应 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>

<!-- LangChain4j OpenAI（兼容 DeepSeek） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>1.15.0</version>
</dependency>
```

**设计要点**：

- **为什么引入 WebFlux？** 传统的 `@RestController` 返回的是一个完整的 HTTP 响应体。SSE 流式对话需要服务端持续地向客户端推送 token 片段——这正是 WebFlux 的 `Flux<String>` 擅长的场景。`Flux<String>` 代表一个异步的、可多次发射元素的数据流，Spring MVC 会将其序列化为 SSE `data:` 事件。
- **为什么只引入** `langchain4j-open-ai` 而不引入 `langchain4j-core`？ `langchain4j-open-ai` 已经传递依赖了 `langchain4j-core`，Maven 会自动引入，无需显式声明。
- **为什么不用** `langchain4j-reactor`？ 最初计划引入这个桥接模块将 LangChain4j 的回调转为 Reactor `Flux`，但发现 `Flux.create()` 已经提供了标准的回调→响应式桥接能力，无需额外依赖。
- **版本选择 1.15.0 而非最新 1.15.1-beta25**：beta 版在国内阿里云 Maven 镜像上可能找不到，1.15.0 是稳定版，功能完全满足需求。

**踩坑记录**：

1. `langchain4j-open-ai:1.15.1-beta25` 在阿里云镜像上 404 → 改为 `1.15.0`
2. `langchain4j-reactor:1.15.0` 同样找不到 → 移除，用 `Flux.create()` 替代
3. `StreamingChatLanguageModel` 在 1.15.0 中已重命名为 `StreamingChatModel` → 改用新类名
4. `com.zora.entity.ChatMessage` 与 `dev.langchain4j.data.message.ChatMessage` 同名冲突 → 后者使用全限定名


---

### 第二步：数据库迁移 — 新增对话和消息表

**操作**：创建 [V2__chat_tables.sql](springboot/src/main/resources/db/migration/V2__chat_tables.sql)。

```sql
CREATE TABLE IF NOT EXISTS chat_conversation (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    title       VARCHAR(200) DEFAULT '新的对话',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS chat_message (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id  BIGINT NOT NULL,
    role             VARCHAR(20) NOT NULL,   -- user / assistant / system
    content          TEXT NOT NULL,
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE
);

CREATE INDEX idx_chat_message_conv ON chat_message(conversation_id, created_at);
```

**设计要点**：

- **两张表的关系**：一个用户 → 多个会话（`chat_conversation`），一个会话 → 多条消息（`chat_message`）。`user_id` 和 `conversation_id` 分别是外键。
- `ON DELETE CASCADE`：删除用户时自动删除其所有会话，删除会话时自动删除其所有消息——避免孤儿数据。这在手动测试时特别有用，删除测试用户不会留下脏数据。
- `title` 默认值 `'新的对话'`：新建会话时不需要传标题，AI 会在第一条回复后自动用用户的问题摘要作为标题。
- `role` 字段：`user`（用户消息）、`assistant`（AI 回复）、`system`（系统提示词）。当前只用前两个，`system` 预留给后续的系统提示词配置功能。
- `content` 用 `TEXT` 而非 `VARCHAR`：AI 回复可能很长（代码块、长文分析），`TEXT` 最大 65535 字节，足够存储单轮对话。
- `BIGINT` 而非 `INT`：消息表预期数据量大，`BIGINT` 自增上限远高于 `INT`，避免未来溢出。
- **索引** `idx_chat_message_conv`：最频繁的查询是"按会话 ID 查消息列表（按时间排序）"，联合索引 `(conversation_id, created_at)` 覆盖这个查询模式。

**Docker 部署**：在 `docker-compose.yml` 中，这个 SQL 挂载到 `/docker-entrypoint-initdb.d/02-chat-tables.sql`，MySQL 容器首次启动时按文件名顺序执行。


---

### 第三步：实体类 — ChatConversation + ChatMessage

**操作**：创建两个实体类。

[**ChatConversation.java**](springboot/src/main/java/com/zora/entity/ChatConversation.java)：

```java
@TableName("chat_conversation")
public class ChatConversation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer userId;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // getter/setter...
}
```

[**ChatMessage.java**](springboot/src/main/java/com/zora/entity/ChatMessage.java)：

```java
@TableName("chat_message")
public class ChatMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
    // getter/setter...
}
```

**命名冲突问题**：LangChain4j 也有一个 `ChatMessage` 类（`dev.langchain4j.data.message.ChatMessage`），在 `AiChatService` 中需要同时使用两种消息类型。解决方案：

- `com.zora.entity.ChatMessage` 用 `import com.zora.entity.*` 通配导入
- `dev.langchain4j.data.message.ChatMessage` 用全限定名 `dev.langchain4j.data.message.ChatMessage` 在代码中直接引用

这样避免了 `import` 冲突，代码中两个 `ChatMessage` 各得其所。


---

### 第四步：Mapper 接口

**操作**：创建两个 Mapper 接口。

[**ChatConversationMapper.java**](springboot/src/main/java/com/zora/mapper/ChatConversationMapper.java)：

```java
public interface ChatConversationMapper extends BaseMapper<ChatConversation> {
}
```

[**ChatMessageMapper.java**](springboot/src/main/java/com/zora/mapper/ChatMessageMapper.java)：

```java
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
```

与 `UserMapper` 一样，继承 `BaseMapper` 即可获得完整的 CRUD 能力，零 XML。


---

### 第五步：AI 配置类 — DeepSeek 模型 Bean

**操作**：创建 [AiConfig.java](springboot/src/main/java/com/zora/config/AiConfig.java)。

```java
@Configuration
public class AiConfig {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${ai.model-name:deepseek-chat}")
    private String modelName;

    @Value("${ai.temperature:0.7}")
    private Double temperature;

    @Value("${ai.max-tokens:4096}")
    private Integer maxTokens;

    @Value("${ai.timeout-seconds:120}")
    private Integer timeoutSeconds;

    @Bean
    public OpenAiStreamingChatModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
```

**设计要点**：

- **为什么用** `OpenAiStreamingChatModel` 而不是专门的 `DeepSeekStreamingChatModel`？ DeepSeek 完全兼容 OpenAI API 格式（`/v1/chat/completions`），所以 LangChain4j 的 OpenAI 实现直接可用。只需把 `baseUrl` 从 `https://api.openai.com/v1` 改为 `https://api.deepseek.com/v1`。这种"兼容 OpenAI 格式"的设计也是 DeepSeek 的核心竞争力之一——所有支持 OpenAI 的工具都能无缝切换到 DeepSeek。
- `StreamingChatModel` 而非 `ChatModel`：`StreamingChatModel` 是流式接口，token 逐个返回（通过回调），适合 SSE 场景。`ChatModel` 是同步接口，等全部生成完才返回，适合后台任务。
- `temperature: 0.7`：控制回答的随机性。0.0 = 完全确定性（每次回答相同），1.0 = 高随机性。0.7 是对话场景的常用值，兼顾创造性和准确性。
- `maxTokens: 4096`：单次回答的最大 token 数。4096 大约能输出 2000-3000 个中文字，覆盖绝大多数对话场景。
- `logRequests: true`：开发阶段在控制台打印请求和响应的完整内容，便于调试。生产环境建议关闭。
- **所有配置值都用** `${ENV_VAR:default}` 模式：Docker 环境通过 `.env` 注入，本地开发直接使用默认值。


---

### 第六步：核心服务 — AiChatService

**操作**：创建 [AiChatService.java](springboot/src/main/java/com/zora/service/AiChatService.java)，这是整个 AI 功能的核心。

#### 6.1 SSE 流式对话

```java
public Flux<String> streamChat(String email, String userMessage, Long conversationId) {
    // 1. 获取或创建会话
    Long convId = getOrCreateConversation(email, conversationId);

    // 2. 保存用户消息到数据库
    saveMessage(convId, "user", userMessage);

    // 3. 构建 LangChain4j 消息列表（系统提示 + 历史 + 当前消息）
    List<dev.langchain4j.data.message.ChatMessage> messages = buildMessages(convId, userMessage);

    // 4. 通过 Flux.create() 桥接回调→SSE
    return Flux.create(sink -> {
        StringBuilder fullResponse = new StringBuilder();

        streamingModel.chat(messages, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                fullResponse.append(token);
                sink.next(token);    // 每收到一个 token 就推送给前端
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                // 流结束：保存完整回复到数据库
                saveMessage(convId, "assistant", fullResponse.toString());
                updateTitleIfFirstMessage(convId, fullResponse.toString());
                sink.complete();
            }

            @Override
            public void onError(Throwable error) {
                sink.error(error);
            }
        });
    });
}
```

**核心流程解析**：

```text
用户发送消息
  ├─ 1. getOrCreateConversation()：有 convId → 查数据库；无 → 新建会话
  ├─ 2. saveMessage()：将用户消息持久化到 chat_message 表
  ├─ 3. buildMessages()：构建 LangChain4j 消息列表
  │    ├─ SystemMessage（系统提示词："你是一个有帮助的 AI 助手"）
  │    ├─ 历史消息（最近 20 条，从数据库加载）
  │    └─ UserMessage（当前用户输入）
  ├─ 4. Flux.create() + StreamingChatResponseHandler：
  │    ├─ onPartialResponse(token) → sink.next(token) → SSE data 事件 → 前端逐字显示
  │    ├─ onCompleteResponse() → 保存完整回复 → sink.complete()
  │    └─ onError() → sink.error()
  └─ 前端收到 SSE 流，逐 token 拼接显示
```

**为什么用** `Flux.create()` 而不是 `Flux.generate()` 或其他？

- `Flux.create()` 适合异步、多线程的场景——LangChain4j 的回调在 HTTP 客户端的 IO 线程上触发，不在调用 `streamChat()` 的请求线程上
- `Flux.generate()` 是同步的，只能在订阅线程上生成元素，不适合回调模式
- `Flux.create()` 的 `sink` 对象可以安全地从任意线程调用 `next()`、`complete()`、`error()`

#### 6.2 多轮上下文构建

```java
private List<dev.langchain4j.data.message.ChatMessage> buildMessages(Long convId, String userMessage) {
    List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

    // 系统提示词
    messages.add(new SystemMessage("你是一个有帮助的 AI 助手。请用中文回答问题。"));

    // 历史消息（最近 20 条）
    List<ChatMessage> history = loadHistory(convId);
    for (ChatMessage msg : history) {
        if ("user".equals(msg.getRole())) {
            messages.add(new UserMessage(msg.getContent()));
        } else if ("assistant".equals(msg.getRole())) {
            messages.add(new AiMessage(msg.getContent()));
        }
    }

    // 当前用户消息
    messages.add(new UserMessage(userMessage));

    return messages;
}
```

**为什么限制 20 条历史？** 大模型的上下文窗口有限（DeepSeek-chat 为 64K token），20 条消息大约消耗 4000-8000 token，留出足够空间给 AI 的回复。超出 20 条的早期消息会被"遗忘"——这是当前 Phase 1 的简化策略，Phase 3 会引入记忆摘要机制。

#### 6.3 会话标题自动生成

```java
private void updateTitleIfFirstMessage(Long convId, String aiResponse) {
    ChatConversation conv = conversationMapper.selectById(convId);
    if ("新的对话".equals(conv.getTitle())) {
        // 用 AI 回复的前 50 个字符作为标题
        String title = aiResponse.length() > 50
                ? aiResponse.substring(0, 50) + "..."
                : aiResponse;
        // 去掉 Markdown 标记
        title = title.replaceAll("[#*`\\[\\]()]", "").trim();
        conv.setTitle(title);
        conversationMapper.updateById(conv);
    }
}
```

**为什么用 AI 回复而不是用户问题做标题？** 用户的问题可能很短（如"你好"），而 AI 回复通常更有信息量。取前 50 字符并去掉 Markdown 标记，生成简洁可读的标题。


---

### 第七步：Controller 层 — AI 对话接口

**操作**：创建 [AiChatController.java](springboot/src/main/java/com/zora/controller/AiChatController.java)。

```java
@RestController
@RequestMapping("/ai")
public class AiChatController {

    @Resource
    private AiChatService chatService;

    /** SSE 流式对话 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestBody Map<String, Object> body,
                                    HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        String message = (String) body.get("message");
        Long conversationId = body.get("conversationId") != null
                ? Long.valueOf(body.get("conversationId").toString()) : null;
        return chatService.streamChat(email, message, conversationId);
    }

    /** 获取对话列表 */
    @GetMapping("/conversations")
    public ResponseUtil listConversations(HttpServletRequest request) { ... }

    /** 新建对话 */
    @PostMapping("/conversations")
    public ResponseUtil createConversation(@RequestBody Map<String, String> body,
                                            HttpServletRequest request) { ... }

    /** 获取对话消息历史 */
    @GetMapping("/conversations/{id}")
    public ResponseUtil getMessages(@PathVariable Long id,
                                     HttpServletRequest request) { ... }

    /** 删除对话 */
    @DeleteMapping("/conversations/{id}")
    public ResponseUtil deleteConversation(@PathVariable Long id,
                                            HttpServletRequest request) { ... }
}
```

**接口汇总**：

|方法 |路径 |功能 |返回格式 |
|---|---|---|---|
|POST |`/ai/chat/stream` |SSE 流式对话 |`text/event-stream` |
|GET |`/ai/conversations` |获取当前用户的对话列表 |JSON |
|POST |`/ai/conversations` |新建对话 |JSON |
|GET |`/ai/conversations/{id}` |获取对话的消息历史 |JSON |
|DELETE |`/ai/conversations/{id}` |删除对话（移至回收站） |JSON |
|POST |`/ai/conversations/batch-delete` |批量软删除对话（移至回收站） |JSON |
|POST |`/ai/conversations/batch-restore` |批量恢复已删除对话 |JSON |
|POST |`/ai/conversations/batch-permanent-delete` |批量永久删除对话 |JSON |

**认证方式**：所有 `/ai/**` 接口都需要登录。`WebConfig` 的拦截器配置 `addPathPatterns("/**")` 已经覆盖了 `/ai/**`，无需额外配置。Controller 从 `request.getAttribute("userEmail")` 获取当前用户邮箱（由 `LoginInterceptor` 设置）。

`produces = MediaType.TEXT_EVENT_STREAM_VALUE`：告诉 Spring MVC 这个接口返回的是 SSE 流（`text/event-stream`），不是普通的 JSON。Spring 会：

1. 设置 `Content-Type: text/event-stream` 响应头
2. 设置 `Cache-Control: no-cache` 响应头
3. 将 `Flux<String>` 的每个元素包装为 `data: {元素}\n\n` 格式发送


---

### 第八步：配置文件 — application.yml + .env

**操作**：在 [application.yml](springboot/src/main/resources/application.yml) 中添加 AI 配置段：

```yaml
# ===== AI 配置 =====
ai:
  api-key: ${AI_API_KEY:sk-xxxxxxxx}
  base-url: ${AI_BASE_URL:https://api.deepseek.com/v1}
  model-name: ${AI_MODEL_NAME:deepseek-chat}
  temperature: ${AI_TEMPERATURE:0.7}
  max-tokens: ${AI_MAX_TOKENS:4096}
  timeout-seconds: ${AI_TIMEOUT_SECONDS:120}
```

在 [.env](.env) 中配置实际值：

```env
# ===== AI 配置 =====
AI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
AI_BASE_URL=https://api.deepseek.com/v1
AI_MODEL_NAME=deepseek-chat
AI_TEMPERATURE=0.7
AI_MAX_TOKENS=4096
AI_TIMEOUT_SECONDS=120
```

**⚠️ 重要**：`AI_API_KEY` 需要替换为你自己的 DeepSeek API Key。注册 [platform.deepseek.com](https://platform.deepseek.com) 即可获取，新用户赠送 500 万 token。


---

### 第九步：Docker Compose 更新

**操作**：修改 [docker-compose.yml](docker-compose.yml)，新增两处配置：

#### 9.1 MySQL 初始化 SQL 追加

```yaml
volumes:
  - ./springboot/src/main/resources/init.sql:/docker-entrypoint-initdb.d/01-init.sql:ro
  - ./springboot/src/main/resources/db/migration/V2__chat_tables.sql:/docker-entrypoint-initdb.d/02-chat-tables.sql:ro
```

`docker-entrypoint-initdb.d/` 目录下的 SQL 文件按文件名顺序执行，`01-init.sql` 先建库建表，`02-chat-tables.sql` 后建 AI 对话表。

#### 9.2 后端环境变量追加

```yaml
backend:
  environment:
    # ... 原有配置 ...
    - AI_API_KEY=${AI_API_KEY}
    - AI_BASE_URL=${AI_BASE_URL}
    - AI_MODEL_NAME=${AI_MODEL_NAME}
```


---

### 第十步：Nginx 代理配置 — SSE 支持

**操作**：修改 [nginx.conf](web/frontend/nginx.conf)，新增 AI 接口代理：

```text
location /ai/ {
    proxy_pass http://backend:8080;
    proxy_read_timeout 120s;      # AI 回复可能需要较长时间
    proxy_buffering off;          # ⚠️ 关键：禁用缓冲，逐 token 转发
    proxy_cache off;              # 禁用缓存
    chunked_transfer_encoding on; # 启用分块传输
}
```

**⚠️** `proxy_buffering off` 是 SSE 的关键配置：

默认情况下，Nginx 会缓冲后端响应，等积累到一定量再转发给客户端。这对 SSE 来说是灾难性的——每个 token 只有几个字节，Nginx 可能会等很久才转发，导致前端收不到实时的 token 流。

设置 `proxy_buffering off` 后，Nginx 收到后端的每一个 chunk 就立即转发给客户端，保证 token 逐字到达前端。

`proxy_read_timeout 120s` 将读取超时设为 2 分钟——DeepSeek 生成长回复可能需要 30-60 秒，默认的 60 秒超时可能不够。


---

### 第十一步：前端 SSE 流式请求 — 原生 fetch

**操作**：创建 [api/ai.js](web/frontend/src/api/ai.js)。

```js
export function streamChat(message, conversationId, onToken, onDone, onError) {
  const token = getToken()
  const controller = new AbortController()

  fetch('/ai/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': token || '',
    },
    body: JSON.stringify({ message, conversationId: conversationId || null }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (response.status === 401) {
        onError(new Error('Token 已过期'))
        return
      }
      // 读取 SSE 流
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break

        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split('\n')
        buffer = lines.pop() || ''

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim()
            if (data) onToken(data)
          }
        }
      }
      onDone()
    })
    .catch((err) => {
      if (err.name !== 'AbortError') onError(err)
    })

  return controller
}
```

**为什么用原生** `fetch` 而不是 `axios`？

Axios 的响应拦截器会将整个响应体缓存在内存中，等请求完成后才返回——这对 SSE 流式场景是错误的。我们需要**逐 chunk 读取**响应体，这只有原生 `fetch` 的 `ReadableStream` API 能做到。

**SSE 数据格式解析**：

服务端发送的 SSE 格式是：

```text
data: 你

data: 好

data: ！

data: [DONE]
```

前端的解析逻辑是：用 `TextDecoder` 将二进制 chunk 解码为文本 → 按 `\n` 分割 → 过滤以 `data:` 开头的行 → 提取 `data:` 后面的内容 → 调用 `onToken()` 回调。

`AbortController`：返回控制器对象，前端可以通过 `controller.abort()` 取消正在进行的流式请求——实现"停止生成"按钮。


---

### 第十二步：前端 Chat 页面 — 完整对话 UI

**操作**：创建 [Chat.vue](web/frontend/src/views/Chat.vue)，约 760 行。

#### 整体布局

```text
┌─────────────────────────────────────────────────────────────┐
│  [≡ 侧边栏]  [返回主页]                                      │
├──────────┬──────────────────────────────────────────────────┤
│ 新对话    │                                                  │
│          │        🤖 你好！我是 DeepSeek AI 助手              │
│ ▸ 对话 1  │           [推荐问题 chips]                        │
│   对话 2  │                                                  │
│   对话 3  │                                                  │
│          │  👤 用户消息（右侧绿色气泡）                        │
│          │  🤖 AI 回复（左侧白色气泡 + Markdown 渲染）         │
│          │     [复制]                                         │
│          │                                                  │
│          │  ┌────────────────────────┐ [发送/停止]            │
│          │  │ 输入消息...             │                       │
│          │  └────────────────────────┘                       │
│          │  AI 由 DeepSeek 大模型驱动，内容仅供参考              │
└──────────┴──────────────────────────────────────────────────┘
```

#### 核心功能点

|功能 |实现方式 |
|---|---|
|对话列表 |`GET /ai/conversations` 加载，侧边栏展示 |
|新建对话 |直接发消息，后端自动创建会话 |
|切换对话 |点击侧边栏项，加载历史消息 |
|删除对话 |二次确认后 `DELETE /ai/conversations/{id}` |
|消息发送 |`fetch` POST `/ai/chat/stream`，SSE 流式接收 |
|逐字显示 |`streamingContent` ref 拼接 token，`v-html` 实时渲染 |
|停止生成 |`AbortController.abort()` 中断 SSE 流 |
|Markdown 渲染 |`marked` 解析 → `highlight.js` 代码高亮 → `DOMPurify` XSS 过滤 |
|代码高亮 |`highlight.js` 支持 190+ 语言，自动检测 + 手动指定 |
|消息复制 |`navigator.clipboard.writeText()` 复制纯文本 |
|自动滚动 |`watch(streamingContent)` + `nextTick()` → `scrollTop = scrollHeight` |
|推荐问题 |欢迎页 6 个 suggestion chips，点击直接发送 |

#### Markdown 渲染管线

```js
const renderMarkdown = (text) => {
  // 1. marked.parse()：Markdown → HTML
  const raw = marked.parse(text, {
    highlight: (code, lang) => {
      // 2. highlight.js：代码块语法高亮
      if (lang && hljs.getLanguage(lang)) {
        return hljs.highlight(code, { language: lang }).value
      }
      return hljs.highlightAuto(code).value
    },
  })
  // 3. DOMPurify.sanitize()：XSS 防护
  return DOMPurify.sanitize(raw)
}
```

**三步管线的设计**：

1. `marked`：将 Markdown 语法（`# 标题`、`` `代码` ``、`| 表格 |`）转换为 HTML 标签
2. `highlight.js`：在 `marked` 的代码块回调中，对 `<pre><code>` 内的代码做语法高亮（添加 `<span class="hljs-keyword">` 等标签）
3. `DOMPurify`：过滤 AI 回复中可能的 XSS 攻击向量（如 `<script>` 标签、`javascript:` URL）

**为什么需要 DOMPurify？** AI 模型的输出是不可信的——攻击者可以通过精心构造的 prompt 让 AI 输出包含恶意 HTML/JS 的内容（"Prompt Injection"）。`v-html` 会直接渲染 HTML，如果没有 XSS 过滤，攻击者可以让 AI 输出 `<script>steal_token()</script>` 并在其他用户的浏览器中执行。

#### 样式设计要点

- **用户消息**：右侧绿色气泡（`#31c27c`），白色文字
- **AI 消息**：左侧白色气泡，带轻微阴影
- **Markdown 样式**：表格、代码块、引用块、标题都有完整样式，与 GitHub Markdown 风格一致
- **代码块**：`#f6f8fa` 浅灰背景，等宽字体，水平滚动
- **行内代码**：`#f0f0f0` 背景，粉红色文字（`#e83e8c`）
- **引用块**：左侧绿色竖线（`#31c27c`），浅绿背景
- **流式生成指示**：AI 助手名称旁有绿色闪烁圆点 `●`（CSS `animation: pulse`）


---

### 第十三步：路由和导航更新

**操作**：修改两个文件。

[**router/index.js**](web/frontend/src/router/index.js) — 添加 `/chat` 路由：

```js
{
  path: '/chat',
  name: 'Chat',
  component: () => import('@/views/Chat.vue'),
  meta: { requiresAuth: true },
}
```

[**Home.vue**](web/frontend/src/views/Home.vue) — 添加 AI 对话入口按钮：

```html
<el-button type="success" @click="$router.push('/chat')">
  <el-icon><ChatDotRound /></el-icon>
  AI 对话
</el-button>
```

`meta: { requiresAuth: true }` 确保未登录用户无法访问 `/chat` 页面——路由守卫会自动跳转到登录页。


---

### 第十四步：Vite 代理配置

**操作**：修改 [vite.config.js](web/frontend/vite.config.js)，新增 `/ai` 代理：

```js
proxy: {
  '/user': {
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
  '/ai': {                         // ← 新增
    target: 'http://localhost:8080',
    changeOrigin: true,
  },
}
```

开发模式下，`/ai/chat/stream` 请求会被 Vite 开发服务器代理到 `http://localhost:8080/ai/chat/stream`，解决跨域问题。


---

## 数据流全景图

```text
用户在 Chat.vue 输入消息并点击发送
  │
  ▼
fetch('/ai/chat/stream') ─── SSE 流式请求 ──→ Nginx (proxy_buffering off)
                                                    │
                                                    ▼
                                              LoginInterceptor
                                              (JWT 校验 + 设置 userEmail)
                                                    │
                                                    ▼
                                              AiChatController.streamChat()
                                              (读取 request.getAttribute("userEmail"))
                                                    │
                                                    ▼
                                              AiChatService.streamChat()
                                              ├─ getOrCreateConversation()
                                              ├─ saveMessage(user)
                                              ├─ buildMessages() → SystemMessage + 历史 + UserMessage
                                              └─ Flux.create() + StreamingChatResponseHandler
                                                    │
                                                    ▼
                                              OpenAiStreamingChatModel.chat()
                                              (POST https://api.deepseek.com/v1/chat/completions)
                                                    │
                                              ┌─────┴─────┐
                                              │ token 逐个 │
                                              │ 通过回调   │
                                              └─────┬─────┘
                                                    │
                                              onPartialResponse(token)
                                              → sink.next(token)
                                                    │
                                                    ▼
                                              SSE data: {token}\n\n
                                                    │
                                                    ▼
                                              前端 fetch ReadableStream
                                              → 解析 data: 行
                                              → onToken(token)
                                              → streamingContent += token
                                              → v-html="renderMarkdown(streamingContent)"
                                              → marked + highlight.js + DOMPurify
                                              → 用户看到逐字出现的 AI 回复
                                                    │
                                              流结束 → onCompleteResponse()
                                              → saveMessage(assistant)
                                              → updateTitleIfFirstMessage()
                                              → sink.complete()
                                              → onDone()
                                              → 流式消息转为正式消息
```


---

## 启动与测试

### 前置条件

1. 注册 DeepSeek 账号并获取 API Key：[platform.deepseek.com](https://platform.deepseek.com)
2. 将 API Key 填入 `.env` 文件的 `AI_API_KEY`

### Docker Compose 启动

```bash
# 一键构建并启动所有服务
docker compose up -d --build

# 查看日志（确认 DeepSeek 连接正常）
docker compose logs -f backend

# 访问前端
# http://localhost → 登录 → 点击 "AI 对话" → 开始对话
```

### 手动启动（开发模式）

```bash
# 后端
cd springboot
mvn spring-boot:run

# 前端
cd web/frontend
npm install
npm run dev

# 访问 http://localhost:3000
```

### 功能验证清单

|# |测试场景 |预期结果 |
|---|---|---|
|1 |未登录访问 `/chat` |跳转到登录页 |
|2 |登录后点击 "AI 对话" |进入 Chat 页面，显示欢迎界面 |
|3 |输入"你好"并发送 |AI 逐字回复，绿色用户气泡 + 白色 AI 气泡 |
|4 |发送包含代码的问题 |AI 回复中的代码块有语法高亮 |
|5 |发送表格类问题 |AI 回复中的表格正确渲染 |
|6 |点击"停止生成" |AI 停止回复，已生成内容保留并标记"已停止生成" |
|7 |刷新页面 |对话列表显示历史会话 |
|8 |点击历史会话 |加载该会话的所有消息 |
|9 |删除对话 |二次确认后删除，侧边栏更新 |
|10 |新建对话 |清空消息区，显示欢迎界面 |


---

## 已知限制与后续优化（Phase 2-4）

|限制 |当前行为 |后续优化 |
|---|---|---|
|~~无知识库~~ |~~AI 仅使用通用知识回答~~ |✅ Phase 2 已完成：RAG 知识库（Apache Tika + 内存向量库） |
|~~无工具调用~~ |~~AI 无法执行搜索、计算等操作~~ |✅ Phase 3.1 已完成：LangChain4j Tool Calling 基础框架 + 结构化 SSE 事件协议 |
|~~内置工具待实现~~ |~~Agent 框架已就绪，但具体工具（搜索/数学/代码）尚未实现~~ |✅ Phase 3.2 已完成：WebSearchTool（Tavily API）+ MathTool（exp4j）+ CodeExecutionTool（JS 沙箱） |
|~~无推理可视化~~ |~~前端仅显示最终回答~~ |✅ Phase 3.3 已完成：Agent 推理面板（思考步骤 + 工具调用展示 + 色彩编码） |
|~~历史截断~~ |~~最多保留 20 条历史消息~~ |✅ Phase 3.4 已完成：记忆摘要 + 长期记忆（RedisChatMemoryStore + LLM 异步摘要） |
|内存向量库需重启重建 |每次重启重新 embed 全部文档 |后续：迁移到 ChromaDB / Qdrant 持久化向量库 |
|~~无多 Agent 协作~~ |~~单一 Agent 处理所有任务~~ |✅ Phase 3.5 已完成：多 Agent 编排（SupervisorAgent + ResearchAgent/MathAgent/CodeAgent + AgentGraph） |
|~~无全文搜索~~ |~~无法跨对话搜索历史消息~~ |✅ Phase 4 已完成：MySQL FULLTEXT + ngram 中文全文搜索 |
|~~无数据仪表盘~~ |~~无使用统计可视化~~ |✅ Phase 4 已完成：ECharts 数据仪表盘（8 个统计端点 + Redis 缓存） |
|~~无用户行为分析~~ |~~无操作追踪~~ |✅ Phase 4 已完成：AOP + @TrackAction + @Async 行为日志 |
|~~无智能推荐~~ |~~无相关内容推荐~~ |✅ Phase 4 已完成：关键词提取 + FULLTEXT 匹配三维推荐 |
|单模型 |仅支持 DeepSeek |后续：多模型切换（GPT-4o、Claude、本地 Ollama） |


---

## 文件变更总览

### 新增文件（8 个）

|文件 |用途 |
|---|---|
|`springboot/src/main/java/com/zora/config/AiConfig.java` |LangChain4j 模型 Bean 配置 |
|`springboot/src/main/java/com/zora/service/AiChatService.java` |AI 对话核心服务（流式 + 持久化） |
|`springboot/src/main/java/com/zora/controller/AiChatController.java` |REST API + SSE 端点 |
|`springboot/src/main/java/com/zora/entity/ChatConversation.java` |对话会话实体 |
|`springboot/src/main/java/com/zora/entity/ChatMessage.java` |对话消息实体 |
|`springboot/src/main/java/com/zora/mapper/ChatConversationMapper.java` |对话 Mapper |
|`springboot/src/main/java/com/zora/mapper/ChatMessageMapper.java` |消息 Mapper |
|`springboot/src/main/resources/db/migration/V2__chat_tables.sql` |数据库迁移 SQL |
|`web/frontend/src/views/Chat.vue` |AI 对话页面（~760 行） |
|`web/frontend/src/api/ai.js` |AI API 封装（含 SSE 流式请求） |

### 修改文件（6 个）

|文件 |变更 |
|---|---|
|`springboot/pom.xml` |新增 WebFlux + LangChain4j 依赖 |
|`springboot/src/main/resources/application.yml` |新增 `ai:` 配置段 |
|`docker-compose.yml` |新增 SQL 挂载 + AI 环境变量 |
|`web/frontend/nginx.conf` |新增 `/ai/` 代理（含 SSE 配置） |
|`web/frontend/vite.config.js` |新增 `/ai` 代理 |
|`web/frontend/src/router/index.js` |新增 `/chat` 路由 |
|`web/frontend/src/views/Home.vue` |新增 "AI 对话" 按钮 |
|`.env` |新增 AI 配置变量 |


---

