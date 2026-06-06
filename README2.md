# Spring Boot + Vue3 AI 增强型全栈系统

> 基于 LangChain4j + DeepSeek 的智能对话系统，集成 JWT 双 Token 认证、SSE 流式传输、Markdown 渲染、Docker 一键部署。

---

## 项目简介

在已有的用户认证系统（JWT 双 Token + 微信 OAuth + RBAC）基础上，新增 **AI 智能对话** 功能。用户登录后可与 DeepSeek 大模型实时对话，支持多轮上下文、对话历史管理、Markdown 代码高亮渲染。

### 核心能力

| 能力 | 说明 |
|------|------|
| 🤖 AI 智能对话 | DeepSeek-V3 大模型驱动，中英文对话、代码生成、数据分析 |
| 📡 SSE 流式传输 | 逐字输出，打字机效果，120 秒超时支持长回复 |
| 📝 Markdown 渲染 | 完整 Markdown 支持：标题、列表、表格、引用、代码块（190+ 语言语法高亮） |
| 🔒 XSS 安全过滤 | DOMPurify 过滤 AI 输出中的潜在恶意 HTML/JS |
| 💬 多轮上下文 | 自动携带最近 20 条历史消息，支持连续对话 |
| 📂 对话管理 | 新建、切换、删除对话，自动从首条回复生成标题 |
| 🛑 中途停止 | 随时停止 AI 生成，已输出内容保留 |
| 🔐 认证保护 | 所有 AI 接口需 JWT 登录，复用现有拦截器链 |

---

## 技术栈

### 后端

| 技术 | 版本 | 作用 |
|------|------|------|
| Spring Boot | 3.5.11 | 基础框架 |
| LangChain4j | 1.15.0 | AI 应用框架（Java 原生，对标 Python LangChain） |
| DeepSeek Chat | V3 | 大语言模型（兼容 OpenAI API） |
| WebFlux | — | SSE 流式响应（`Flux<String>`） |
| MyBatis-Plus | 3.5.12 | 对话/消息持久化 |
| MySQL | 8.x | 存储对话和消息 |
| Redis | 7.x | Token 缓存（复用认证系统） |

### 前端

| 技术 | 版本 | 作用 |
|------|------|------|
| Vue 3 | 3.5 | 前端框架 |
| marked | — | Markdown → HTML 转换 |
| highlight.js | — | 代码语法高亮（190+ 语言） |
| DOMPurify | — | XSS 防护（过滤 AI 输出） |
| Element Plus | 2.14 | UI 组件库 |
| 原生 fetch | — | SSE 流式读取（Axios 不支持） |

---

## 快速开始

### 1. 获取 DeepSeek API Key

1. 注册 [platform.deepseek.com](https://platform.deepseek.com)
2. 创建 API Key
3. 新用户赠送 500 万 token，足够开发测试

### 2. 配置环境变量

编辑项目根目录的 `.env` 文件：

```env
AI_API_KEY=sk-your-deepseek-api-key-here
AI_BASE_URL=https://api.deepseek.com/v1
AI_MODEL_NAME=deepseek-chat
AI_TEMPERATURE=0.7
AI_MAX_TOKENS=4096
AI_TIMEOUT_SECONDS=120
```

### 3. Docker Compose 一键启动

```bash
docker compose up -d --build
```

访问：
- **前端**：http://localhost
- **API 文档**：http://localhost/doc.html
- **健康检查**：http://localhost:18080/actuator/health

### 4. 手动启动（开发模式）

```bash
# 后端
cd springboot
mvn spring-boot:run

# 前端
cd web/frontend
npm install
npm run dev
```

访问 http://localhost:3000

---

## 功能截图

### AI 对话界面

```
┌─────────────────────────────────────────────────────────────┐
│ [≡]  返回主页                                                │
├──────────┬──────────────────────────────────────────────────┤
│ [+新对话] │                                                  │
│          │        🤖 你好！我是 DeepSeek AI 助手              │
│ ▸ 对话 1  │           [推荐问题: 用Java写单例...]              │
│   对话 2  │                                                  │
│          │  👤 帮我写一个快速排序                              │
│          │                                                  │
│          │  🤖 好的，以下是 Java 实现的快速排序：              │
│          │  ```java                                         │
│          │  public class QuickSort {                         │
│          │      public void sort(int[] arr) { ... }         │
│          │  }                                               │
│          │  ```                                             │
│          │     [复制]                                        │
│          │                                                  │
│          │  ┌────────────────────────┐ [发送]                │
│          │  │ 输入消息...             │                       │
│          │  └────────────────────────┘                       │
│          │  AI 由 DeepSeek 大模型驱动，内容仅供参考              │
└──────────┴──────────────────────────────────────────────────┘
```

---

## API 接口

### AI 对话

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| POST | `/ai/chat/stream` | SSE 流式对话 | ✅ |
| GET | `/ai/conversations` | 获取对话列表 | ✅ |
| POST | `/ai/conversations` | 新建对话 | ✅ |
| GET | `/ai/conversations/{id}` | 获取对话消息 | ✅ |
| DELETE | `/ai/conversations/{id}` | 删除对话（移至回收站） | ✅ |
| GET | `/ai/conversations/trash` | 获取回收站列表 | ✅ |
| POST | `/ai/conversations/{id}/restore` | 恢复已删除对话 | ✅ |
| DELETE | `/ai/conversations/{id}/permanent` | 永久删除对话 | ✅ |

### 认证接口（已有）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/user/register` | 邮箱验证码注册 |
| POST | `/user/login` | 密码登录（返回双 Token） |
| POST | `/user/refresh` | 刷新 accessToken |
| POST | `/user/logout` | 登出 |
| POST | `/user/wechat/qrcode` | 微信扫码登录 |
| GET | `/user/captcha` | 获取图形验证码 |

---

## 架构设计

### 整体架构

```
┌─────────────┐     SSE/JSON      ┌──────────────┐     OpenAI API     ┌──────────┐
│   Vue 3     │ ←───────────────→ │ Spring Boot  │ ←───────────────→ │ DeepSeek │
│   前端      │    Nginx 代理      │   后端        │    HTTPS          │   API    │
│   Chat.vue  │                   │ AiChatService│                   │          │
└─────────────┘                   └──────────────┘                   └──────────┘
      │                                 │
      │ localStorage                    │ MySQL + Redis
      │ (JWT Token)                     │ (对话/消息/Token)
      ▼                                 ▼
┌─────────────┐                   ┌──────────────┐
│  浏览器      │                   │   数据库      │
└─────────────┘                   └──────────────┘
```

### SSE 流式传输链路

```
DeepSeek API (chunked response)
  → OpenAiStreamingChatModel (HTTP client)
    → StreamingChatResponseHandler.onPartialResponse(token)
      → Flux.create() sink.next(token)
        → Spring MVC (text/event-stream)
          → Nginx (proxy_buffering off)
            → 浏览器 fetch ReadableStream
              → 解析 data: 行 → onToken()
                → streamingContent += token
                  → v-html="renderMarkdown(streamingContent)"
                    → 用户看到逐字出现的回复
```

### 数据库设计

```sql
-- 用户表（已有）
user(id, email, password, role)

-- AI 对话会话表
chat_conversation(id, user_id → user.id, title, created_at, updated_at)

-- AI 对话消息表
chat_message(id, conversation_id → chat_conversation.id, role, content, created_at)
```

---

## 项目结构

```
springboot/
├── src/main/java/com/zyt/
│   ├── config/
│   │   ├── AiConfig.java              # LangChain4j 模型配置
│   │   ├── Knife4jConfig.java         # OpenAPI 3 / Knife4j 文档配置
│   │   ├── SwaggerCompatController.java # /swagger-resources 兼容端点
│   │   ├── WebConfig.java             # 拦截器配置（已覆盖 /ai/**）
│   │   └── SecurityConfig.java        # Spring Security（仅 BCrypt）
│   ├── controller/
│   │   ├── AiChatController.java      # AI 对话 REST API + SSE
│   │   └── UserController.java        # 用户认证 API
│   ├── service/
│   │   ├── AiChatService.java         # AI 对话核心逻辑
│   │   └── impl/UserServiceImpl.java  # 用户认证逻辑
│   ├── entity/
│   │   ├── ChatConversation.java      # 对话会话实体
│   │   ├── ChatMessage.java           # 对话消息实体
│   │   └── User.java                  # 用户实体
│   └── mapper/
│       ├── ChatConversationMapper.java
│       ├── ChatMessageMapper.java
│       └── UserMapper.java
├── src/main/resources/
│   ├── application.yml                # 配置文件
│   └── db/migration/
│       └── V2__chat_tables.sql        # AI 表迁移
└── pom.xml

web/frontend/
├── src/
│   ├── views/
│   │   ├── Chat.vue                   # AI 对话页面
│   │   ├── Home.vue                   # 首页（含 AI 入口）
│   │   ├── Login.vue                  # 登录页
│   │   └── Register.vue               # 注册页
│   ├── api/
│   │   ├── ai.js                      # AI API（含 SSE 流式）
│   │   └── user.js                    # 用户 API
│   ├── router/index.js                # 路由配置
│   └── utils/token.js                 # Token 管理
├── nginx.conf                         # Nginx 配置（含 /ai/ 代理）
└── vite.config.js                     # Vite 配置（含 /ai 代理）
```

---

## 开发指南

### 添加新的 AI 端点

1. 在 `AiChatController` 中添加方法
2. 在 `AiChatService` 中实现业务逻辑
3. 在 `api/ai.js` 中添加前端调用函数
4. `/ai/**` 路径自动被拦截器保护，无需额外配置

### 切换 LLM 提供商

修改 `.env` 中的三个变量即可：

```env
# 切换到 OpenAI
AI_API_KEY=sk-your-openai-key
AI_BASE_URL=https://api.openai.com/v1
AI_MODEL_NAME=gpt-4o

# 切换到本地 Ollama
AI_API_KEY=ollama
AI_BASE_URL=http://localhost:11434/v1
AI_MODEL_NAME=qwen2.5
```

LangChain4j 的 `OpenAiStreamingChatModel` 兼容所有 OpenAI API 格式的服务商。

### 自定义系统提示词

编辑 `AiChatService.java` 中的 `buildMessages()` 方法：

```java
messages.add(new SystemMessage("你是一个专业的 Java 后端工程师，擅长 Spring Boot 和微服务架构。"));
```

---

## 路线图

### ✅ Phase 1：AI 智能对话（已完成）

- LangChain4j + DeepSeek 集成
- SSE 流式传输
- 多轮上下文对话
- Markdown + 代码高亮渲染
- 对话历史管理
- Docker 一键部署

### 🔜 Phase 2：RAG 知识库

- ChromaDB 向量数据库
- 文档上传与解析（PDF、Word、Markdown）
- 文档分块 + Embedding 向量化
- RAG 检索增强生成
- 知识库管理界面

### 🔜 Phase 3：AI Agent 智能体

- LangChain4j Tool Calling
- 内置工具：网页搜索、数学计算、代码执行
- Agent 可视化推理过程
- 记忆摘要 + 长期记忆
- LangGraph4j 多 Agent 编排

### 🔜 Phase 4：智能搜索与分析

- 全文搜索引擎
- 对话数据分析仪表盘
- 用户行为分析
- 智能推荐

---

## 常见问题

### Q: API Key 无效怎么办？

检查 `.env` 中的 `AI_API_KEY` 是否正确，确保没有多余的空格或换行。可以在 DeepSeek 控制台查看 key 状态。

### Q: AI 回复很慢怎么办？

- 检查网络连接到 `api.deepseek.com` 是否正常
- 减小 `AI_MAX_TOKENS` 值（如 2048）可以加快回复速度
- 使用 `deepseek-chat` 模型（比 `deepseek-reasoner` 快）

### Q: 如何查看 AI 请求日志？

后端启动时 `AiConfig` 中 `logRequests: true` 会打印完整的请求和响应。查看后端日志：

```bash
docker compose logs -f backend
```

### Q: SSE 流式传输中断怎么办？

- 检查 Nginx 配置中 `proxy_buffering off` 是否生效
- 检查 `proxy_read_timeout` 是否足够（建议 120s）
- 前端的 `AbortController` 可以主动取消请求

### Q: 如何添加更多 LLM 模型？

LangChain4j 支持 50+ 模型提供商。添加新依赖后在 `AiConfig` 中创建新的 Bean，使用 `@Qualifier` 注解区分即可。

---

## 相关文档

- [项目构建教程](项目构建教程.md) — 用户认证系统完整构建过程
- [AI 功能构建教程](项目构建教程2.md) — AI 增强功能详细实现
- [CLAUDE.md](CLAUDE.md) — 项目架构与开发规范
- [API 文档](http://localhost:8080/doc.html) — Knife4j 在线文档（需启动后端）

---

## 许可证

本项目仅供学习交流使用。
