# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## 工作规范

每次发起对话，请遵循：

- 新增功能/修 bug：代码注释写好、接口文档及时更新、测试用例要编写、相应 md 文件要更新
- 修改现有功能：先跑通现有 403 个单元测试，确保不引入回归
- 新增公共端点：必须加入 `WebConfig.excludePathPatterns`
- 新增 API 路径前缀：同步更新 `vite.config.js`（本地开发）和 `nginx.conf`（Docker）

---

## Build & Run

### 开发模式（推荐）

```bash
# 1. 启动基础设施
docker compose up -d mysql redis

# 2. 后端（热加载，IDEA 打开 AppStart.java 按 F5）
cd springboot && mvn spring-boot:run    # → :8080

# 3. 前端（HMR）
cd web/frontend && npm run dev          # → :3000

# 4. 测试
cd springboot && mvn test -Dtest='!*IntegrationTest'   # 403 单元测试，~20s
```

热加载原理：`spring-boot-devtools` 保存即重启（2-3s），Vite HMR 保存即浏览器原地更新组件。

### Docker 一键启动

```bash
docker compose up -d --build   # → http://localhost
docker compose down -v         # 停止并清数据
```

### 环境变量

所有敏感配置通过项目根目录 `.env` 文件管理。IDEA `F5` 启动自动加载（`.vscode/launch.json` 已配置 `envFile`）。终端启动需手动 export。

```env
# 多模型配置（Phase 5.3）
AI_PROVIDERS_0_NAME=deepseek
AI_PROVIDERS_0_BASE_URL=https://api.deepseek.com/v1
AI_PROVIDERS_0_API_KEY=sk-xxx
AI_PROVIDERS_0_MODELS_0_ID=deepseek-v4-flash
AI_PROVIDERS_0_MODELS_0_NAME=DeepSeek-V4-Flash
AI_PROVIDERS_0_DEFAULT_MODEL=deepseek-v4-flash
# 追加其他 Provider: AI_PROVIDERS_1_*, AI_PROVIDERS_2_* ...
```

---

## 项目结构（关键路径）

```
springboot/src/main/java/com/zora/
├── AppStart.java                        # 启动类（@MapperScan + @EnableScheduling）
├── config/                              # 配置类
│   ├── AiConfig.java                    #   模型 Bean 定义（委托 ModelRegistry）
│   ├── AiProperties.java                #   @ConfigurationProperties(prefix="ai")
│   ├── ModelRegistry.java               #   多模型注册中心（Phase 5.3）
│   ├── AgentConfig.java                 #   @ConfigurationProperties(prefix="agent")
│   ├── RagConfig.java                   #   EmbeddingModel + SimpleEmbeddingStore
│   ├── WebConfig.java                   #   拦截器注册 + CORS + 公开路径排除
│   ├── SecurityConfig.java              #   仅 BCrypt，其余全部禁用
│   ├── Knife4jConfig.java               #   OpenAPI 3 文档
│   ├── auth/                            #   认证鉴权
│   │   ├── LoginInterceptor.java        #     JWT 校验 + Redis 单设备验证
│   │   ├── RoleInterceptor.java         #     RBAC 角色校验
│   │   └── RequireRole.java             #     @RequireRole("admin") 注解
│   ├── tracking/                        #   行为追踪（Phase 4）
│   │   ├── TrackAction.java             #     @TrackAction 注解
│   │   ├── ActionLogAspect.java         #     AOP 切面（主线程提取，委托异步写入）
│   │   └── ActionLogWriter.java         #     @Async 写入组件
│   └── task/CleanupTask.java            #   定时清理
├── controller/                          # 7 个 Controller，44+ 端点
├── service/                             # 8 个 Service 接口 + 实现（含 SimpleEmbeddingStore）
├── entity/                              # 实体类 + dto/ 子包（PageResult, SearchResult, AgentStep）
├── mapper/                              # 8 个 Mapper + 1 个 XML（ChatMessageMapper.xml）
├── exception/                           # 7 个异常类 + ErrorCode 枚举 + GlobalExceptionHandler
├── agent/                               # AI Agent 智能体
│   ├── AgentService.java + impl/
│   ├── event/AgentEvent.java            #   SSE 结构化事件 record
│   ├── tool/{Tool,WebSearch,Math,Code}  #   工具标记接口 + 3 个内置工具
│   ├── memory/RedisChatMemoryStore.java #   LangChain4j ChatMemoryStore Redis 实现
│   └── graph/{AgentState,Supervisor,...}#   多 Agent 编排（Phase 3.5）
└── utils/                               # 8 个工具类
    ├── UserContext.java                 #   ThreadLocal 用户上下文缓存（Phase 5.2）
    ├── JwtUtil.java, ResponseUtil.java
    └── ...

web/frontend/src/
├── views/                               # 8 个页面组件（Chat, KnowledgeBase, Search, Dashboard 等）
├── components/chat/                     # 7 个 Chat 子组件（Phase 5.1）
├── components/charts/                   # 4 个 ECharts 图表组件
├── composables/                         # useScroll.js, useReasoning.js（Phase 5.1）
├── api/                                 # 7 个 API 模块（含拦截器自动刷新 Token）
└── router/index.js                      # 路由 + beforeEach 导航守卫
```

---

## 架构关键模式

### 认证：自定义 Interceptor，非 Spring Security

`SecurityConfig` 禁用所有 Filter（CSRF/session/form-login），仅保留 `BCryptPasswordEncoder`。实际认证由 `LoginInterceptor`（Spring MVC HandlerInterceptor）完成：

```
LoginInterceptor → JWT 校验 → Redis 比对（单设备）→ 写入 request attribute
RoleInterceptor  → 检查 @RequireRole 注解 → 403 if 权限不足
```

### 双 JWT Token 系统

| Token | type claim | TTL | Redis Key | 用途 |
|-------|-----------|-----|-----------|------|
| accessToken | `"access"` | 30 min | `token:{email}` | 每次 API 请求 |
| refreshToken | `"refresh"` | 7 days | `refresh_token:{email}` | 仅 `/user/refresh` |

**P0 安全规则**：`/user/refresh` 必须先调用 `jwtUtil.isRefreshToken(token)` 校验 type 为 refresh，防止 accessToken 无限续期。

### Redis 状态管理

所有运行时状态存 Redis，TTL 自动清理。Key 格式 `prefix:identifier`。

| Prefix | TTL | 用途 |
|--------|-----|------|
| `token:` / `refresh_token:` | 30min / 7d | 双 Token |
| `email_code:` / `reset_code:` | 5min | 验证码 |
| `captcha:` | 1min | 图形验证码 |
| `login_fail:` | 15min | 暴力破解计数（≥5 锁定）|
| `agent_rate:` | 1min 滑动窗口 | Agent 限流（10次/分钟）|

### 异常处理

`@RestControllerAdvice` 全局捕获，异常层次：`BusinessException` → `BadRequestException(400)` / `UnauthorizedException(401)` / `ForbiddenException(403)` / `NotFoundException(404)` / `RateLimitException(429)`。Interceptor 错误走 `ResponseUtil.toJsonError()` 直接写 PrintWriter。Service 层可抛异常（推荐）或返回 ResponseUtil（复杂控制流），两种模式共存。

### 多模型支持（Phase 5.3）

`ModelRegistry` 启动时从 `AiProperties` 读取所有 Provider 配置，创建 `ChatModel` / `StreamingChatModel` 实例。`AiConfig` 中旧 Bean 名（`chatLanguageModel`, `streamingChatLanguageModel`）保持不变，指向默认 Provider 的默认模型。前端通过 `GET /ai/models`（无需登录）获取模型列表，el-dropdown 切换。

### UserContext（Phase 5.2）

`utils/UserContext.java` — ThreadLocal 缓存用户信息，消除所有 ServiceImpl 中重复的 `findUserByEmail()` 方法。主要方法：`getEmail()`（从 request attribute 取）、`getUserId()`（查 DB 并缓存，用户不存在抛 NotFoundException）。

### 前端数据流

```
Vue 组件 → api/*.js → Axios 实例（拦截器注入 accessToken）
  → 401 → isRefreshing 锁 + pendingRequests 队列 → 刷新 → 重放
```

Token 存储：localStorage 两个独立 key（`zyt_access_token`, `zyt_refresh_token`）。路由守卫仅 UX，真正安全在后端 Interceptor。

---

## 关键约定

- **新增公开端点**：必须加 `WebConfig.excludePathPatterns`，否则返回 401
- **API 路径前缀同步**：新增如 `/xxx` 前缀，必须同时更新 `vite.config.js` 和 `nginx.conf`。SSE 端点需 `proxy_buffering off` + 延长 `proxy_read_timeout`
- **密码存储**：必须用注入的 `BCryptPasswordEncoder`，禁止 `new`
- **JWT claims**：`generateAccessToken(email, role)` 写 `type=access`，`generateRefreshToken(email, role)` 写 `type=refresh`。所有调用方必须传 role
- **登录错误提示**：统一返回"邮箱或密码错误"，禁止区分"用户不存在"/"密码错误"（防用户枚举）
- **邮件发送**：必须异步（`new Thread(() -> ...).start()`），SMTP 慢会阻塞 HTTP 响应
- **前端路径别名**：`@` → `src/`（vite.config.js 配置）
- **测试文件中文命名**：`@DisplayName("中文描述")`，`@Nested` 分组
- **Knife4j 兼容**：`GlobalExceptionHandler` 有 `@Hidden` 注解（避免 SpringDoc 扫描导致 NoSuchMethodError），`Knife4jConfig` 有 `GlobalOpenApiCustomizer` 手动注入 SecurityRequirement

---

## 测试策略

```bash
mvn test -Dtest='!*IntegrationTest'   # 403 单元测试（纯 Mock，无外部依赖）
mvn test -Dtest="XxxIntegrationTest"  # 集成测试（需要 Docker，Testcontainers）
```

三层 Mock：纯 JUnit 5 → Mockito + ReflectionTestUtils → Standalone MockMvc。不使用 `@WebMvcTest`（因 `@MapperScan` 导致 Spring 上下文加载失败），统一用 `MockMvcBuilders.standaloneSetup()`。

集成测试基类 `AbstractIntegrationTest`：Testcontainers 启动 MySQL 8 + Redis 7，`@MockitoBean` mock AI 模型 Bean，`spring.sql.init` 自动建表。多个 IntegrationTest 类并行运行会因 Spring Context 缓存冲突超时，建议单独运行。

---

## 数据库迁移

| 版本 | 文件 | 内容 |
|------|------|------|
| V2 | `V2__chat_tables.sql` | chat_conversation, chat_message |
| V3 | `V3__rag_tables.sql` | knowledge_base, kb_document, kb_chunk |
| V4 | `V4__agent_tables.sql` | chat_conversation_summary + summary_id 列 |
| V5 | `V5__search_index.sql` | chat_message/conversation FULLTEXT 索引（ngram） |
| V6 | `V6__user_action_log.sql` | user_action_log 表 |
| V7 | `V7__multi_model.sql` | chat_conversation 新增 model_provider, model_id 列 |

Docker Compose 通过 `docker-entrypoint-initdb.d` 挂载全部迁移脚本自动初始化。Testcontainers 通过 `spring.sql.init.schema-locations` 加载。
