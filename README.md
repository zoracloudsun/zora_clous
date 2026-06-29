# 🤖 AI 全栈智能平台：认证系统 + AI 智能对话 + RAG 知识库 + AI Agent + 智能搜索与分析

> 前后端分离的Spring Boot + Vue3 全栈 AI 对话平台 | 个人/家庭/小团队专属 AI Agent | 2026

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.11-brightgreen)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D)](https://vuejs.org/)
[![JDK](https://img.shields.io/badge/JDK-21-orange)](https://openjdk.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-blue)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-7.x-red)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-2496ED)](https://www.docker.com/)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-1.15.0-00B265)](https://docs.langchain4j.dev/)
[![Multi-Model](https://img.shields.io/badge/AI-Multi%20Model-8B5CF6)](https://platform.deepseek.com/)
[![Tests](https://img.shields.io/badge/Tests-418%20passed-success)](springboot/)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)
[![CI](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml/badge.svg)](https://github.com/zoracloudsun/zora_clous/actions/workflows/ci.yml)

---

## 项目简介

一套**生产级**前后端分离的全栈 AI 对话平台，集成用户认证、大模型对话（多模型支持）、RAG 知识库、AI Agent 智能体、智能搜索与分析。

| 系统                  | 说明                                                                                                                                       |
| --------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| 🔐**用户认证系统**    | 图形验证码、邮箱验证码注册、JWT 双 Token 鉴权、微信 OAuth 扫码登录、RBAC 角色权限控制、暴力破解防护                                        |
| 🤖**AI 智能对话**     | 多模型支持（DeepSeek / OpenAI / Ollama / 硅基流动等）、SSE 流式传输、多轮上下文对话、Markdown + 代码高亮、对话历史管理、模型选择器         |
| 📚**RAG 知识库**      | 文档上传 → 文本提取 → 分块 → Embedding 向量化 → 检索增强生成，让 AI 基于用户自己的文档回答                                                 |
| 🧠**AI Agent 智能体** | LangChain4j Tool Calling，AI 可自主调用工具（搜索/计算/代码执行），推理可视化面板，多 Agent 协作（Supervisor → 专家 → 聚合），长期记忆摘要 |
| 🔍**智能搜索与分析**  | MySQL FULLTEXT 全文搜索（ngram 中文分词）+ ECharts 数据仪表盘 + 用户行为追踪（AOP + @TrackAction）+ 智能推荐                               |

- **后端**：Spring Boot 3.5.11 + MyBatis-Plus 3.5.12 + MySQL 8 + Redis 7 + LangChain4j 1.15.0 + Apache Tika + Tavily Search + exp4j + JJWT 0.12 + Spring AOP
- **前端**：Vue 3.5 + Vite 8 + Element Plus 2.14 + Vue Router 4 + Axios + marked + highlight.js + DOMPurify + ECharts 6

---

## 核心功能

### 用户认证

| 功能           | 说明                                                                   |
| -------------- | ---------------------------------------------------------------------- |
| 图形验证码     | Java AWT 绘制，6位大写字母 + 干扰线 + 噪点，Redis TTL 2min             |
| 邮箱验证码注册 | 163邮箱 SMTP，6位数字验证码，60秒防刷                                  |
| 密码登录       | BCrypt 加密 + 5次失败锁定15分钟 + 统一错误提示防用户枚举               |
| 双 Token 鉴权  | accessToken (30min) + refreshToken (7天)，JWT`type` claim 强制类型校验 |
| 无感刷新       | Axios 响应拦截器 + 请求队列，并发 401 只刷新一次                       |
| 单设备登录     | Redis 缓存 Token，新登录自动踢掉旧设备                                 |
| 微信扫码登录   | 真实 OAuth 2.0，已绑定用户扫一扫直接登录，新用户扫码后邮箱绑定         |
| 邮箱找回密码   | 验证码校验 + 重置后强制所有设备重新登录                                |
| RBAC 角色权限  | 自定义`@RequireRole` 注解 + RoleInterceptor，user/admin 双角色         |

### AI 智能对话

| 功能             | 说明                                                           |
| ---------------- | -------------------------------------------------------------- |
| 🤖 多模型支持    | DeepSeek-V3 / DeepSeek-R1 / OpenAI GPT / Ollama 本地模型等     |
| 🔄 模型选择器    | 前端 el-dropdown 切换模型，无需修改配置重启                    |
| 📡 SSE 流式传输  | 逐字输出，打字机效果，120秒超时支持长回复                      |
| 📝 Markdown 渲染 | 标题、列表、表格、引用、代码块（190+ 语言语法高亮）            |
| 🔒 XSS 安全过滤  | DOMPurify 过滤 AI 输出中的潜在恶意 HTML/JS                     |
| 💬 多轮上下文    | 自动携带最近 20 条历史消息，支持连续对话                       |
| 📂 对话管理      | 新建、切换、删除、回收站恢复、永久删除、批量操作、自动生成标题 |
| 🛑 中途停止      | 随时停止 AI 生成，已输出内容保留                               |

### RAG 知识库

| 功能          | 说明                                                                                                       |
| ------------- | ---------------------------------------------------------------------------------------------------------- |
| 📄 文档解析   | Apache Tika 提取 PDF/DOCX/DOC/TXT/MD                                                                       |
| ✂️ 智能分块   | 递归分割，800字符/块，100字符重叠                                                                          |
| 🔢 向量嵌入   | OpenAI 兼容 Embedding API（支持 OpenAI / 硅基流动 / Ollama）                                               |
| 🔍 向量检索   | 余弦相似度检索，内存向量库 + MySQL 持久化                                                                  |
| 🔄 启动重建   | 应用重启自动从 MySQL 重建向量索引                                                                          |
| 🗂️ 知识库管理 | 卡片列表、文档表格、处理状态轮询、检索测试面板                                                             |
| ♻️ 两级回收站 | 知识库级回收站（全局）+ 文档级回收站（按知识库），支持恢复（自动重嵌入向量）和永久删除（清理文件/向量/DB） |

### AI Agent 智能体

| 功能             | 说明                                                                                             |
| ---------------- | ------------------------------------------------------------------------------------------------ |
| 🔧 工具调用      | LangChain4j Tool Calling 框架，AI 可自主判断并调用搜索/计算/代码执行等工具                       |
| 🌐 网页搜索      | WebSearchTool — Tavily Search API，搜索互联网获取实时信息                                        |
| 🧮 数学计算      | MathTool — exp4j 安全表达式求值，支持三角函数/对数/阶乘等 30+ 运算                               |
| 💻 代码执行      | CodeExecutionTool — JDK ScriptEngine JS 沙箱，超时保护 + 输出截断（默认关闭）                    |
| 🧠 多 Agent 协作 | SupervisorAgent 意图分类 → 专家 Agent 执行（Research/Math/Code）→ Summarizer 聚合结果            |
| 📊 推理可视化    | 实时展示 Agent 思考过程、工具调用和结果，颜色编码面板，可折叠/展开                               |
| 💾 长期记忆      | Redis ChatMemory 24h 缓存 + LLM 异步摘要生成（每 10 条消息触发），对话超窗口后仍能"记住"早期内容 |
| 🛡️ 三层降级      | 多 Agent → 标准 Agent → 直接回答，任何异常都不会导致无回答                                       |
| 🔒 安全防护      | 推理循环最多 5 次迭代 + 10 次/分钟限流 + 18 种 Prompt Injection 检测                             |

### 智能搜索与分析

| 功能            | 说明                                                                             |
| --------------- | -------------------------------------------------------------------------------- |
| 🔍 全文搜索     | MySQL 8 InnoDB FULLTEXT + ngram parser 中文分词，跨对话搜索消息内容              |
| 🌐 搜索结果高亮 | Java 层关键词`<mark>` 标签高亮，前端 DOMPurify 安全过滤                          |
| 📊 数据仪表盘   | ECharts 6 可视化图表：消息趋势、活跃时段、对话趋势、消息占比、功能排行           |
| 📈 统计维度     | 总览摘要卡片 + 双线折线图（用户/AI 消息）+ 柱状图（活跃时段）+ 饼图（角色占比）  |
| 🔎 用户行为分析 | AOP 切面 +`@TrackAction` 注解 + `@Async` 异步日志写入，不阻塞主流程              |
| 📝 行为追踪     | 6 种行为类型自动采集：对话创建、消息发送、搜索查询、KB 上传、KB 检索、Agent 调用 |
| 💡 智能推荐     | 关键词提取 + MySQL FULLTEXT 匹配：相关对话、建议问题、热门知识库                 |
| ⚡ Redis 缓存   | 统计数据 TTL 分级缓存（30min~4h）+ 推荐缓存 30min                                |
| 🎨 推荐卡片     | Chat 侧边栏嵌入 RecommendCard，3 个 Tab 展示相关对话/问题/知识库                 |

---

## 快速开始

### 环境要求

| 组件              | 版本 | 用途                                                |
| ----------------- | ---- | --------------------------------------------------- |
| JDK               | 21+  | Spring Boot 3.x                                     |
| MySQL             | 8.x  | 数据持久化                                          |
| Redis             | 7.x  | Token 缓存 + 验证码 + 向量状态                      |
| Node.js           | 18+  | 前端构建                                            |
| Docker            | —    | 一键启动（可选，替代手动安装）                      |
| AI API Key        | —    | 任一 OpenAI 兼容提供商（DeepSeek/OpenAI/Ollama 等） |
| Embedding API Key | —    | RAG 向量嵌入（硅基流动推荐，国内直连）              |
| 163邮箱           | —    | 发送验证码（可选，需开启 SMTP）                     |
| 微信测试号        | —    | 微信扫码登录（可选）                                |
| Tavily API Key    | —    | Agent 网页搜索工具（可选，免费 1000 次/月）         |

### 1. 克隆项目

```bash
git clone https://github.com/zoracloudsun/zora_clous
cd zora_clous
```

### 2. 配置环境变量

编辑项目根目录的 `.env` 文件：

```env
# ===== MySQL =====
MYSQL_ROOT_PASSWORD=root123
MYSQL_DATABASE=springboot_zyt

# ===== JWT =====
JWT_SECRET=your-jwt-secret-key-change-in-production

# ===== AI 多模型配置（支持任意 OpenAI 兼容提供商）=====
AI_PROVIDERS_0_NAME=deepseek
AI_PROVIDERS_0_BASE_URL=https://api.deepseek.com/v1
AI_PROVIDERS_0_API_KEY=sk-your-deepseek-api-key
AI_PROVIDERS_0_MODELS_0_ID=deepseek-v4-flash
AI_PROVIDERS_0_MODELS_0_NAME=DeepSeek-V4-Flash
AI_PROVIDERS_0_MODELS_1_ID=deepseek-v4-pro
AI_PROVIDERS_0_MODELS_1_NAME=DeepSeek-V4-Pro
AI_PROVIDERS_0_DEFAULT_MODEL=deepseek-v4-flash
AI_TEMPERATURE=0.7
AI_MAX_TOKENS=4096
AI_TIMEOUT_SECONDS=120

# ===== RAG 嵌入模型（OpenAI 兼容）=====
# 推荐硅基流动 (SiliconFlow) — 国内直连、价格便宜
AI_EMBEDDING_API_KEY=sk-your-embedding-key
AI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
AI_EMBEDDING_MODEL=bge-large-zh-v1.5

# ===== Agent 智能体 =====
TAVILY_API_KEY=tvly-your-tavily-api-key
# 工具开关（默认启用搜索和数学，代码执行需手动开启）
# AGENT_TOOL_WEB_SEARCH=true
# AGENT_TOOL_MATH=true
# AGENT_TOOL_CODE_EXEC=false
```

> **获取 API Key**：
>
> - DeepSeek：[platform.deepseek.com](https://platform.deepseek.com)（新用户赠送 500 万 token）
> - 硅基流动（Embedding）：[siliconflow.cn](https://siliconflow.cn)（国内直连，推荐）
> - 添加其他模型：在 `.env` 中追加 `AI_PROVIDERS_1_*` 配置即可
> - 不配置 Embedding API 不影响普通 AI 对话，但知识库功能不可用

### 3. 生产模式：Docker Compose 一键启动

```bash
# 构建并启动所有服务（MySQL + Redis + Spring Boot + Nginx）
docker compose up -d --build

# 查看服务状态
docker compose ps

# 访问
# 前端:        http://localhost
# API 文档:    http://localhost/doc.html
# 健康检查:    http://localhost:18080/actuator/health

# 可选：数据库管理界面
docker compose --profile debug up -d     # Adminer → http://localhost:18081

# 停止（保留数据卷）
docker compose down

# 停止并重置（删除数据库和 Redis 数据）
docker compose down -v
```

> 首次构建需要下载 Docker 镜像和 Maven 依赖，约 3-5 分钟。后续启动只需 `docker compose up -d`，秒级完成。

### 4. 开发模式：Docker + 本地热加载（推荐日常开发）

基础设施（MySQL + Redis）运行在 Docker 中，后端和前端在宿主机运行，支持**修改代码即生效**。

<details>
<summary>点击展开详细步骤</summary>

#### 4.1 启动 Docker 基础设施

```bash
docker compose up -d mysql redis
```

#### 4.2 启动后端（热加载）

```bash
cd springboot
mvn spring-boot:run          # → http://localhost:8080
```

后端依赖 `spring-boot-devtools`，保存代码后 ~3 秒自动重启。

#### 4.3 启动前端（HMR）

```bash
cd web/frontend
npm install                   # 首次运行
npm run dev                   # → http://localhost:4000
```

前端使用 Vite HMR，保存文件后浏览器原地更新组件。

#### 4.4 热加载原理

| 组件        | 工具                 | 行为                                                |
| ----------- | -------------------- | --------------------------------------------------- |
| 后端 Java   | spring-boot-devtools | 保存文件 → Spring 自动重启（2-3s）                  |
| 前端 Vue    | Vite HMR             | 保存文件 → 浏览器原地更新组件（无刷新、无状态丢失） |
| MySQL/Redis | Docker               | 无代码变更 → 启动一次，保持运行                     |

#### 4.5 运行测试

```bash
cd springboot
mvn test -Dtest='!*IntegrationTest'   # 403 个单元测试，~20s
mvn test -Dtest="BruteForceIntegrationTest"  # 集成测试（需要 Docker）
```

#### 4.6 设置管理员

```bash
docker exec auth-mysql mysql -u root -proot123 springboot_zyt \
  -e "UPDATE user SET role = 'admin' WHERE email = 'your_admin@example.com';"
```

</details>

### 5. 手动启动（不使用 Docker）

<details>
<summary>点击展开手动启动步骤</summary>

#### 初始化数据库

```bash
mysql -u root -p < springboot/src/main/resources/init.sql
mysql -u root -p springboot_zyt < DB_MIGRATION.sql
```

#### 启动后端

```bash
cd springboot
mvn spring-boot:run           # → http://localhost:8080
mvn test                      # 运行测试
```

#### 启动前端

```bash
cd web/frontend
npm install
npm run dev                   # → http://localhost:4000
```

</details>

### 6. 开始使用

1. 访问 `http://localhost:4000`（开发模式）或 `http://localhost`（Docker 模式）
2. **注册**：点击「注册账号」→ 输入邮箱 → 发送验证码 → 设置密码
3. **登录**：邮箱 + 密码 + 图形验证码（或切换到微信扫码登录）
4. **AI 对话**：登录后进入「AI 对话」→ 在模型选择器中选择模型 → 发送消息体验流式对话
5. **RAG 知识库**：进入「知识库」→ 创建知识库 → 上传文档（PDF/DOCX/TXT/MD）→ 开启 RAG 开关
6. **AI Agent**：打开「Agent」开关 → 发送需要搜索/计算的问题 → 观察推理面板实时展示 Agent 思考过程
7. **多 Agent**：设置 `AGENT_MULTI_AGENT=true` → Agent 自动路由给领域专家（研究/数学/代码）
8. **批量管理**：对话列表右键"..."菜单进入批量管理模式（全选 + 批量删除/恢复/永久删除）
9. **管理后台**：设置管理员后访问 `/admin` 查看所有注册用户

---

## 项目结构

```
├── springboot/                              # 后端 Spring Boot 工程
│   ├── pom.xml                              # Maven 配置
│   ├── Dockerfile                           # 多阶段构建（Maven → JRE）
│   └── src/
│       ├── main/java/com/zora/
│       │   ├── AppStart.java                # 启动类
│       │   ├── config/                      # 配置类（19 个，含子包）
│       │   │   ├── AiConfig.java            #   模型 Bean 定义（委托 ModelRegistry）
│       │   │   ├── AiProperties.java        #   多模型配置属性（@ConfigurationProperties）
│       │   │   ├── ModelRegistry.java       #   多模型注册中心（启动时创建所有模型实例）
│       │   │   ├── AgentConfig.java         #   Agent 配置（工具开关/Tavily/记忆）
│       │   │   ├── RagConfig.java           #   Embedding 模型 + 向量存储 Bean
│       │   │   ├── Knife4jConfig.java       #   OpenAPI 3 文档
│       │   │   ├── SecurityConfig.java      #   Spring Security（仅 BCrypt）
│       │   │   ├── WebConfig.java           #   拦截器注册 + CORS
│       │   │   ├── MyConfig.java            #   MyBatis-Plus 分页插件
│       │   │   ├── WechatConfig.java        #   微信 OAuth 配置
│       │   │   ├── SwaggerCompatController.java
│       │   │   ├── AsyncConfig.java         #   异步线程池配置
│       │   │   ├── auth/                    #   认证鉴权子包
│       │   │   │   ├── LoginInterceptor.java    # JWT 校验 + Redis 单设备验证
│       │   │   │   ├── RoleInterceptor.java     # RBAC 角色校验
│       │   │   │   └── RequireRole.java         # @RequireRole 自定义注解
│       │   │   ├── tracking/                #   行为追踪子包
│       │   │   │   ├── TrackAction.java         # @TrackAction 注解
│       │   │   │   ├── ActionLogAspect.java     # AOP 切面（主线程提取）
│       │   │   │   └── ActionLogWriter.java     # @Async 异步写入
│       │   │   └── task/                    #   定时任务子包
│       │   │       └── CleanupTask.java         # 定时清理
│       │   ├── controller/                  # REST 控制器（7 个，44+ 端点）
│       │   │   ├── UserController.java      #   用户认证（16 端点）
│       │   │   ├── AiChatController.java    #   AI 对话 + 批量操作 + GET /ai/models
│       │   │   ├── AgentController.java     #   Agent SSE 流式对话
│       │   │   ├── RagController.java       #   RAG 知识库 CRUD（16 端点）
│       │   │   ├── SearchController.java    #   全文搜索
│       │   │   ├── StatisticsController.java #  数据仪表盘（8 端点）
│       │   │   └── RecommendController.java  #  智能推荐
│       │   ├── service/                     # 业务逻辑层（8 个 Service 接口 + 实现）
│       │   │   ├── UserService.java / impl/UserServiceImpl.java
│       │   │   ├── AiChatService.java / impl/AiChatServiceImpl.java
│       │   │   ├── RagService.java / impl/RagServiceImpl.java
│       │   │   ├── RagProcessingService.java / impl/RagProcessingServiceImpl.java
│       │   │   ├── ConversationSummaryService.java / impl/ConversationSummaryServiceImpl.java
│       │   │   ├── SearchService.java / impl/SearchServiceImpl.java
│       │   │   ├── StatisticsService.java / impl/StatisticsServiceImpl.java
│       │   │   ├── RecommendService.java / impl/RecommendServiceImpl.java
│       │   │   └── impl/SimpleEmbeddingStore.java  # 自实现余弦相似度向量存储
│       │   ├── entity/                      # 实体类
│       │   │   ├── User.java, ChatConversation.java, ChatMessage.java
│       │   │   ├── KnowledgeBase.java, KbDocument.java, KbChunk.java
│       │   │   ├── ChatConversationSummary.java
│       │   │   ├── UserActionLog.java       #   用户行为日志
│       │   │   └── dto/                     #   DTO 子包
│       │   │       ├── PageResult.java      #     通用分页 DTO
│       │   │       ├── SearchResult.java    #     搜索结果 DTO
│       │   │       └── AgentStep.java       #     Agent 推理步骤 POJO
│       │   ├── mapper/                      # MyBatis-Plus Mapper（8 个 + 1 XML）
│       │   ├── exception/                   # 异常体系（7 个类）
│       │   │   ├── BusinessException.java   #   基类（支持 ErrorCode 枚举）
│       │   │   ├── ErrorCode.java           #   错误码枚举
│       │   │   ├── BadRequestException / UnauthorizedException / ForbiddenException
│       │   │   ├── NotFoundException / RateLimitException
│       │   │   └── GlobalExceptionHandler.java
│       │   ├── agent/                       # AI Agent 智能体
│       │   │   ├── AgentService.java
│       │   │   ├── impl/AgentServiceImpl.java
│       │   │   ├── event/AgentEvent.java
│       │   │   ├── tool/{Tool,WebSearchTool,MathTool,CodeExecutionTool}.java
│       │   │   ├── memory/RedisChatMemoryStore.java
│       │   │   └── graph/{AgentState,AgentNode,SupervisorAgent,...}.java
│       │   └── utils/                       # 工具类（8 个）
│       │       ├── UserContext.java         #   ThreadLocal 用户上下文缓存
│       │       ├── JwtUtil.java / CaptchaUtil.java / EmailUtil.java
│       │       ├── WechatUtil.java / ResponseUtil.java
│       │       └── FileTypeUtil.java / TextSplitterUtil.java
│       └── test/
│           ├── java/com/zora/               # 单元测试（403 个）
│           │   ├── utils/                   #   工具类测试
│           │   ├── service/                 #   Service 层测试（含 Agent）
│           │   ├── config/                  #   拦截器测试
│           │   ├── controller/              #   Controller 测试
│           │   ├── agent/                   #   Agent 工具测试
│           │   ├── exception/               #   异常处理测试
│           │   └── integration/             #   集成测试（15 个，Testcontainers）
│           │       ├── AbstractIntegrationTest.java
│           │       ├── BruteForceIntegrationTest.java (5)
│           │       ├── TokenFlowIntegrationTest.java (5)
│           │       └── RagPipelineIntegrationTest.java (5)
│           └── resources/
│               ├── application.yml          #   测试配置
│               └── schema-test.sql          #   Testcontainers 建表脚本
├── web/frontend/                            # 前端 Vue 3 + Vite 工程
│   ├── package.json
│   ├── vite.config.js                       # Vite 配置（代理 + Element Plus 按需导入）
│   ├── index.html
│   ├── Dockerfile                           # 多阶段构建（Node → Nginx）
│   ├── nginx.conf                           # Nginx 配置（SPA + SSE + API 代理）
│   └── src/
│       ├── main.js                          # Vue 应用入口
│       ├── App.vue                          # 根组件
│       ├── style.css                        # 全局样式
│       ├── views/                           # 页面组件（8 个）
│       │   ├── Home.vue                     #   首页
│       │   ├── Login.vue / Register.vue / ForgotPassword.vue
│       │   ├── Chat.vue                     #   AI 对话页（模型选择器 + RAG/Agent 开关 + 推理面板）
│       │   ├── KnowledgeBase.vue            #   知识库管理
│       │   ├── Search.vue                   #   全文搜索
│       │   ├── Dashboard.vue                #   数据仪表盘
│       │   └── Admin.vue                    #   管理员页面
│       ├── components/
│       │   ├── chat/                        #   Chat 子组件（7 个）
│       │   │   ├── ChatMessageList.vue      #     消息列表
│       │   │   ├── ChatMessageBubble.vue    #     消息气泡
│       │   │   ├── ChatInput.vue            #     输入区域
│       │   │   ├── ChatSidebar.vue          #     侧边栏（对话列表 + 推荐卡片）
│       │   │   ├── ChatScrollButton.vue     #     滚动到底按钮
│       │   │   ├── ChatTimelineBar.vue      #     电量时间线
│       │   │   └── ChatAgentPanel.vue       #     Agent 推理面板
│       │   ├── charts/                      #   ECharts 图表组件（4 个）
│       │   │   ├── BaseChart.vue / LineChart.vue / BarChart.vue / PieChart.vue
│       │   └── RecommendCard.vue            #   智能推荐卡片
│       ├── composables/                     # 组合式函数
│       │   ├── useScroll.js                 #   滚动逻辑
│       │   └── useReasoning.js              #   Agent 推理步骤管理
│       ├── api/                             # API 封装层（7 个）
│       │   ├── index.js                     #   Axios 实例 + 拦截器（自动刷新 Token）
│       │   ├── user.js / ai.js / agent.js / rag.js
│       │   ├── search.js / statistics.js / recommend.js
│       ├── router/index.js                  # 路由 + 导航守卫
│       └── utils/token.js                   # localStorage 双 Token 存取
├── docker-compose.yml                       # Docker 容器编排
├── .env                                     # 环境变量
├── CLAUDE.md                                # Claude Code 项目指南
├── FIX_Document/                            # 问题修复文档
└── Project Detail Guide/                    # 项目构建详细教程（5 个 Phase）
```

---

## API 概览

### 用户认证 — `/user/**`

| 方法 | 路径                              |        认证         | 说明                                  |
| ---- | --------------------------------- | :-----------------: | ------------------------------------- |
| GET  | `/user/captcha`                   |         否          | 获取图形验证码                        |
| POST | `/user/send-code`                 |         否          | 发送邮箱验证码                        |
| POST | `/user/register`                  |         否          | 邮箱验证码注册                        |
| POST | `/user/login`                     |         否          | 密码登录（5次失败锁定）               |
| POST | `/user/logout`                    |     accessToken     | 登出                                  |
| POST | `/user/refresh`                   |    refreshToken     | 刷新 accessToken（强制 type=refresh） |
| GET  | `/user/me`                        |     accessToken     | 获取当前用户信息                      |
| POST | `/user/wechat/qrcode`             |         否          | 生成微信扫码场景                      |
| GET  | `/user/wechat/check`              |         否          | 轮询扫码状态                          |
| GET  | `/user/wechat/callback`           |         否          | 微信 OAuth 回调                       |
| POST | `/user/wechat/bind-email`         |         否          | 微信扫码后绑定邮箱                    |
| POST | `/user/forgot-password/send-code` |         否          | 发送密码重置验证码                    |
| POST | `/user/forgot-password/reset`     |         否          | 重置密码                              |
| GET  | `/user/admin/users`               | accessToken + admin | 管理员分页查询用户                    |

### AI 对话 — `/ai/**` + `/agent/**`

| 方法   | 路径                                       | 说明                                      | 认证 |
| ------ | ------------------------------------------ | ----------------------------------------- | :--: |
| GET    | `/ai/models`                               | 获取可用模型列表（多模型支持）            |  否  |
| POST   | `/ai/chat/stream`                          | SSE 流式对话                              |  ✅  |
| POST   | `/ai/chat/rag-stream`                      | SSE RAG 流式对话                          |  ✅  |
| GET    | `/ai/conversations`                        | 获取对话列表                              |  ✅  |
| POST   | `/ai/conversations`                        | 新建对话                                  |  ✅  |
| GET    | `/ai/conversations/{id}`                   | 获取对话消息                              |  ✅  |
| DELETE | `/ai/conversations/{id}`                   | 删除对话（移至回收站）                    |  ✅  |
| POST   | `/ai/conversations/{id}/restore`           | 恢复已删除对话                            |  ✅  |
| DELETE | `/ai/conversations/{id}/permanent`         | 永久删除对话                              |  ✅  |
| POST   | `/ai/conversations/batch-delete`           | 批量软删除（最多 50 个）                  |  ✅  |
| POST   | `/ai/conversations/batch-restore`          | 批量恢复                                  |  ✅  |
| POST   | `/ai/conversations/batch-permanent-delete` | 批量永久删除                              |  ✅  |
| POST   | `/agent/chat/stream`                       | Agent SSE 流式对话（工具调用+结构化事件） |  ✅  |

### RAG 知识库 — `/rag/**`

| 方法   | 路径                                  | 说明              | 认证 |
| ------ | ------------------------------------- | ----------------- | :--: |
| POST   | `/rag/knowledge-bases`                | 创建知识库        |  ✅  |
| GET    | `/rag/knowledge-bases`                | 列出用户的知识库  |  ✅  |
| GET    | `/rag/knowledge-bases/{id}`           | 获取知识库详情    |  ✅  |
| PUT    | `/rag/knowledge-bases/{id}`           | 更新知识库        |  ✅  |
| DELETE | `/rag/knowledge-bases/{id}`           | 软删除知识库      |  ✅  |
| POST   | `/rag/knowledge-bases/{id}/documents` | 上传文档（≤10MB） |  ✅  |
| POST   | `/rag/knowledge-bases/{id}/query`     | 测试检索          |  ✅  |
| GET    | `/rag/recycle-bin`                    | 知识库回收站      |  ✅  |
| PUT    | `/rag/recycle-bin/{kbId}/restore`     | 恢复知识库        |  ✅  |
| DELETE | `/rag/recycle-bin/{kbId}`             | 永久删除知识库    |  ✅  |
| ...    | （共 16 个端点，含文档级回收站）      |                   |      |

### 智能搜索与分析 — `/search/**` `/statistics/**` `/recommend/**`

| 方法 | 路径                             | 说明                                     | 认证 |
| ---- | -------------------------------- | ---------------------------------------- | :--: |
| GET  | `/search/messages`               | MySQL FULLTEXT 全文搜索 + 高亮           |  ✅  |
| GET  | `/statistics/overview`           | 数据总览（会话/消息/活跃天数/AI 使用率） |  ✅  |
| GET  | `/statistics/message-trend`      | 消息趋势（user/assistant 双线折线图）    |  ✅  |
| GET  | `/statistics/active-hours`       | 24 小时活跃热力图                        |  ✅  |
| GET  | `/statistics/conversation-trend` | 对话创建趋势                             |  ✅  |
| GET  | `/statistics/message-ratio`      | 用户 vs AI 消息占比（饼图）              |  ✅  |
| GET  | `/statistics/knowledge-stats`    | 知识库使用统计                           |  ✅  |
| GET  | `/statistics/action-ranking`     | 功能使用排行（user_action_log 聚合）     |  ✅  |
| GET  | `/statistics/weekly-activity`    | 最近 7 天活跃度                          |  ✅  |
| GET  | `/recommend/suggestions`         | 智能推荐（相关对话/建议问题/热门知识库） |  ✅  |

> 📖 在线调试：启动后访问 `http://localhost:8080/doc.html`（Knife4j 接口文档，支持在线发送请求）

---

## 架构设计

### 整体架构

```
┌────────────────────┐     SSE/JSON/HTTP    ┌───────────────────┐     OpenAI API       ┌────────────────┐
│    Vue 3 前端      │ ←─────────────────→ │  Spring Boot 后端 │ ←─────────────────→  │  多模型提供商   │
│                    │     Nginx 代理       │                   │     HTTPS            │                │
│  • Login/Register  │                      │  • 用户认证 (JWT) │                      │  • DeepSeek    │
│  • Chat + 模型选择 │                      │  • AI 对话 (LLM)  │                      │  • OpenAI      │
│  • KnowledgeBase   │                      │  • RAG 知识库     │                      │  • Ollama      │
│  • Search/Dashboard│                      │  • Agent 智能体   │                      │  • 硅基流动     │
│  • RecommendCard   │                      │  • 全文搜索       │                      │  • 任意兼容     │
└────────┬───────────┘                      │  • 数据统计       │                      └────────────────┘
         │                                  │  • 行为追踪       │
         │ localStorage                     │  • 智能推荐       │
         │ (JWT Token)                      └─────────┬─────────┘
         ▼                                            │
┌──────────────────┐                      MySQL (用户/对话/消息/知识库/行为日志)
│    浏览器        │                      Redis (Token/验证码/状态/统计缓存)
└──────────────────┘
```

### 认证鉴权链路

```
浏览器 → Vue Router（路由守卫）
       → Axios 请求拦截器（注入 accessToken）
       → LoginInterceptor（JWT 校验 + Redis 单设备验证）
       → RoleInterceptor（@RequireRole 注解检查）
       → Controller → Service → Mapper → DB
```

### 双 Token 自动刷新

```
accessToken 过期（30min）
  → 后端 401 → Axios 响应拦截器
    → isRefreshing 锁（并发请求只刷新一次）
    → POST /user/refresh（校验 type=refresh）
    → 新 accessToken → 重放 pendingRequests → 重试原请求
  → 用户完全无感知

refreshToken 也过期（7天）→ 清除 Token → 跳转登录页
```

### RAG 知识库管道

```
上传文档 → Apache Tika 文本提取 → 递归分块（800 chars / 100 overlap）
  → OpenAI 兼容 Embedding → SimpleEmbeddingStore 内存向量库（余弦相似度）
     ↓
RAG 对话 → embed(问题) → 检索 Top-K 相关块 → 注入 System Prompt → SSE 流式输出
```

### AI Agent 推理与编排

```
用户消息 → AgentController → AgentServiceImpl
  ├── 多 Agent 模式（可选）：Supervisor 意图分类 → 专家执行 → Summarizer 聚合
  ├── 标准 Agent ReAct：非流式推理循环（≤5 次）→ 流式最终回答
  └── 降级：直接流式回答
SSE 事件: thinking → tool_call → tool_result → token → done/error
```

### 异常处理

7 类异常 + ErrorCode 枚举，`@RestControllerAdvice` 全局捕获：

```
Service 抛出异常 → GlobalExceptionHandler → ResponseUtil JSON
  → Axios 响应拦截器 → ElMessage.error()
```

---

## 安全特性

| 层级        | 机制                  | 说明                                                        |
| ----------- | --------------------- | ----------------------------------------------------------- |
| 传输层      | HTTPS（生产环境）     | ngrok 开发环境自动提供 HTTPS                                |
| 认证层      | JWT HMAC-SHA256       | 签名防篡改，30min 短时效降低泄露风险                        |
| 类型校验    | JWT`type` claim       | `/refresh` 强制校验 `type=refresh`，防 accessToken 无限续期 |
| 密码存储    | BCrypt                | 自适应哈希，自带随机盐，抗彩虹表                            |
| 防刷        | 图形验证码 + 60s 间隔 | 同邮箱 60 秒内不可重复发送                                  |
| 防暴力破解  | Redis INCR + HTTP 429 | 5 次失败锁定 15 分钟，TTL 首次设置防绕过                    |
| 防用户枚举  | 统一错误提示          | 登录失败统一返回「邮箱或密码错误」                          |
| 单设备登录  | Redis Token 缓存      | 新登录自动踢掉旧设备                                        |
| XSS 防护    | DOMPurify             | 过滤 AI 输出中的潜在恶意 HTML/JS                            |
| AI 注入防护 | System Prompt 规则    | 禁止模型执行敏感操作，限制回答范围                          |
| CSRF        | 显式禁用 + JWT 无状态 | JWT 不依赖 Cookie，天然免疫 CSRF                            |

---

## 技术栈

### 后端

| 技术            | 版本   | 用途                                               |
| --------------- | ------ | -------------------------------------------------- |
| Spring Boot     | 3.5.11 | 基础框架                                           |
| MyBatis-Plus    | 3.5.12 | ORM（BaseMapper + 分页插件）                       |
| MySQL           | 8.x    | 持久化存储 + FULLTEXT 全文搜索（ngram）            |
| Redis           | 7.x    | Token 缓存 + 验证码 + 行为计数 + 统计缓存          |
| LangChain4j     | 1.15.0 | AI 应用框架（流式对话 + Embedding + Tool Calling） |
| DeepSeek Chat   | V3/R1  | 默认大语言模型（OpenAI 兼容 API）                  |
| Apache Tika     | 2.9.2  | 文档文本提取（PDF/DOCX/DOC/TXT/MD）                |
| Tavily Search   | —      | Agent 网页搜索工具 API                             |
| exp4j           | 0.4.8  | Agent 安全数学表达式求值引擎                       |
| Spring Security | 6.x    | BCrypt 密码加密                                    |
| jjwt            | 0.12.6 | JWT 生成/解析/类型校验                             |
| JavaMail        | 3.5.11 | 163 邮箱 SMTP 验证码发送                           |
| Knife4j         | 4.5.0  | OpenAPI 3 接口文档 + 在线调试                      |
| Testcontainers  | 1.20.x | 集成测试（MySQL 8 + Redis 7 容器）                 |

### 前端

| 技术         | 版本 | 用途                                          |
| ------------ | ---- | --------------------------------------------- |
| Vue          | 3.5  | 前端框架（Composition API +`<script setup>`） |
| Vite         | 8.x  | 构建工具                                      |
| Element Plus | 2.14 | UI 组件库（按需导入）                         |
| Vue Router   | 4.x  | 前端路由 + 导航守卫                           |
| Axios        | 1.16 | HTTP 客户端 + 拦截器                          |
| qrcode       | 1.5  | 前端 Canvas 二维码生成                        |
| marked       | —    | Markdown → HTML 转换                          |
| highlight.js | —    | 代码语法高亮（190+ 语言）                     |
| DOMPurify    | —    | XSS 防护                                      |
| ECharts      | 6    | 数据可视化图表（折线图/柱状图/饼图）          |

---

## 测试

**框架**：JUnit 5 + Mockito + Spring MockMvc + Testcontainers

```bash
cd springboot

# 单元测试（403 个，无需 Docker，~20s）
mvn test -Dtest='!*IntegrationTest'

# 单个集成测试类（需要 Docker）
mvn test -Dtest="BruteForceIntegrationTest"      # 5 tests — 暴力破解锁定
mvn test -Dtest="TokenFlowIntegrationTest"       # 5 tests — 双 Token 流程
mvn test -Dtest="RagPipelineIntegrationTest"     # 5 tests — RAG 管道
```

**三层 Mock 策略**：

| 层级                          | 模式                                | 适用场景              |
| ----------------------------- | ----------------------------------- | --------------------- |
| 纯 JUnit 5                    | `@Test` only                        | 无依赖的工具类        |
| Mockito + ReflectionTestUtils | `@InjectMocks` / `@Mock` / `@Spy`   | Service + 拦截器      |
| Standalone MockMvc            | `MockMvcBuilders.standaloneSetup()` | Controller + 异常处理 |

**集成测试**（Phase 5.4）：Testcontainers 启动真实 MySQL 8 + Redis 7 容器，验证组件协作边界。AI 模型 Bean 用 `@MockitoBean` mock，不调外部 API。

---

## 切换 / 添加 AI 模型

项目已内置多模型支持（Phase 5.3），通过 `ModelRegistry` 在启动时创建所有配置的模型实例。

**前端切换**：对话页顶部模型选择器下拉菜单，即时切换无需重启。

**添加新模型**：在 `.env` 中追加 Provider 配置：

```env
# 示例：添加 OpenAI GPT-4o
AI_PROVIDERS_1_NAME=openai
AI_PROVIDERS_1_BASE_URL=https://api.openai.com/v1
AI_PROVIDERS_1_API_KEY=sk-your-openai-key
AI_PROVIDERS_1_MODELS_0_ID=gpt-4o
AI_PROVIDERS_1_MODELS_0_NAME=GPT-4o

# 示例：添加本地 Ollama
AI_PROVIDERS_2_NAME=ollama
AI_PROVIDERS_2_BASE_URL=http://localhost:11434/v1
AI_PROVIDERS_2_API_KEY=ollama
AI_PROVIDERS_2_MODELS_0_ID=qwen2.5
AI_PROVIDERS_2_MODELS_0_NAME=Qwen2.5
```

> `GET /ai/models` 端点无需登录，前端启动时自动加载可用模型列表。

---

## 路线图

### ✅ Phase 1：用户认证系统

- 图形验证码 + 邮箱验证码注册 + 密码登录
- JWT 双 Token 鉴权 + 无感刷新 + 单设备登录
- 微信 OAuth 扫码登录 + RBAC 角色权限
- 暴力破解防护 + 全局异常处理 + Docker 化 + CI/CD
- 171 个单元测试

### ✅ Phase 2：AI 智能对话 + RAG 知识库

- LangChain4j + DeepSeek 集成 + SSE 流式传输 + Markdown 渲染
- Apache Tika 文档解析 + 递归分块 + OpenAI 兼容 Embedding
- 自实现余弦相似度向量存储 + 启动向量索引重建
- 知识库管理界面 + 两级回收站 + 212 个单元测试

### ✅ Phase 3：AI Agent 智能体

- 两阶段流式架构 + ReAct 循环 + 结构化 SSE 事件协议
- 内置工具：WebSearchTool + MathTool + CodeExecutionTool
- Agent 推理可视化面板 + 思考指示器
- 记忆摘要 + 长期记忆（ChatMemory + ConversationSummary）
- 多 Agent 编排（Supervisor → Specialist → Summarizer）
- 166 个 Agent 相关测试

### ✅ Phase 4：智能搜索与分析

- MySQL FULLTEXT 全文搜索（ngram 中文分词）+ 高亮
- ECharts 6 数据仪表盘（8 个统计端点）+ Redis 缓存
- 用户行为分析（AOP + @TrackAction + @Async 异步日志）
- 智能推荐（Bigram 分词 + FULLTEXT 匹配）

### ✅ Phase 5：代码质量重构

- **5.1** Chat.vue 拆分为 7 个子组件 + 2 个 composables
- **5.2** 后端规范化：UserContext（消除 6 处重复代码）+ PageResult<T></t> 通用分页 + ErrorCode 枚举 + config 子包分类 + entity/dto 子包
- **5.3** 多模型支持：ModelRegistry + GET /ai/models + 前端模型选择器
- **5.4** 集成测试基础设施：Testcontainers + BruteForce(5) + TokenFlow(5) + RAG(5)
- **5.5** 性能优化：vue-virtual-scroller 依赖就绪（待全量集成）

---

## 文档索引

| 文档                                                                                  | 说明                                                     |
| ------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| [CLAUDE.md](CLAUDE.md)                                                                | Claude Code 项目开发指南（架构规范 + 关键约定）          |
| [01-authentication-system.md](Project%20Detail%20Guide/01-authentication-system.md)   | 用户认证系统 28 步构建教程                               |
| [02-ai-chat.md](Project%20Detail%20Guide/02-ai-chat.md)                               | AI 智能对话：DeepSeek + SSE 流式 + Markdown 渲染         |
| [03-rag-knowledge-base.md](Project%20Detail%20Guide/03-rag-knowledge-base.md)         | RAG 知识库：Tika + Embedding + 向量检索 + 两级回收站     |
| [04-ai-agent.md](Project%20Detail%20Guide/04-ai-agent.md)                             | AI Agent 智能体：Tool Calling + 多 Agent 编排 + 记忆系统 |
| [05-smart-search-analytics.md](Project%20Detail%20Guide/05-smart-search-analytics.md) | 智能搜索与分析：FULLTEXT + ECharts + 行为追踪 + 推荐     |
| [WECHAT_SETUP_GUIDE.md](Project%20Detail%20Guide/WECHAT_SETUP_GUIDE.md)               | 微信扫码登录完整配置指南                                 |
| [P0_SECURITY_FIX.md](FIX_Document/P0_SECURITY_FIX.md)                                 | P0 安全修复：Token 类型校验 + 暴力破解防护               |
| [P1_SECURITY_FIX.md](FIX_Document/P1_SECURITY_FIX.md)                                 | P1 安全修复                                              |
| [LOCAL_VS_DOCKER_PROXY.md](FIX_Document/LOCAL_VS_DOCKER_PROXY.md)                     | 本地开发 vs Docker 代理配置同步指南                      |
| [Windows_Reserved_Port_8080_FIX.md](FIX_Document/Windows_Reserved_Port_8080_FIX.md)   | Windows 保留端口 8080 排查修复                           |

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
