# Phase 5: 代码质量提升与多模型支持 — 设计文档

## 背景

Phase 1-4 完成后，项目功能已覆盖用户认证、AI 流式对话、RAG 知识库、Agent 智能体、全文搜索、数据仪表盘、用户行为分析和智能推荐。但代码层面积累了一些结构性问题：

- [Chat.vue](web/frontend/src/views/Chat.vue) 3113 行单文件巨石，包含侧边栏、消息列表、输入框、Agent 面板、时间线、滚动按钮——全部耦合在一起
- 后端 Service 层存在重复模式（`findUserByEmail` 散落 6+ 处），返回类型不一致（`Map<String, Object>` / `ResponseUtil` / `List` 混用）
- `config/` 包混杂 5 种不同职责（纯配置、拦截器、注解、切面、定时任务）
- 仅支持 DeepSeek 单一模型，无法切换到其他主流模型

## 目标

在不动现有功能的前提下，通过渐进式重构提升代码质量，为后续新功能开发铺平道路。重构后新增多模型支持。

## 阶段划分

### 5.1 Chat.vue 组件拆分

**原则**：每个组件 ≤ 300 行，职责单一，通过 props/emits 通信，不依赖全局 store。

#### 组件树

```
views/Chat.vue                    (~300 行，编排器)
├─ components/chat/
│  ├─ ChatSidebar.vue            侧边栏：品牌、新对话按钮、对话列表、搜索、右键菜单、批量模式
│  ├─ ChatMessageList.vue        消息列表：消息气泡渲染、自动滚动、虚拟滚动包装
│  ├─ ChatMessageBubble.vue      单条消息气泡：Markdown 渲染、头像、角色标签、操作按钮
│  ├─ ChatInput.vue              输入区：textarea、药丸按钮(Agent/RAG)、上传按钮、发送按钮
│  ├─ ChatAgentPanel.vue         Agent 推理面板：思考步骤/工具调用/工具结果可折叠时间线
│  ├─ ChatTimelineBar.vue        右侧电量条：用户消息锚点、hover 展开/收起、点击跳转
│  └─ ChatScrollButton.vue       ↓ 滚动按钮：sticky 定位、流式加载动画、点击平滑滚动
├─ composables/
│  ├─ useChat.js                 SSE 流式对话核心逻辑
│  ├─ useConversations.js        对话列表 CRUD、批量操作
│  ├─ useReasoning.js            Agent 推理步骤管理
│  └─ useScroll.js               滚动守卫 + 虚拟滚动
```

#### 组件接口

| 组件 | Props (入) | Emits (出) | 自管状态 |
|------|-----------|------------|---------|
| ChatSidebar | `conversations`, `currentId`, `collapsed` | `select`, `new`, `delete`, `batch-*` | 搜索词、右键菜单、批量模式 |
| ChatMessageList | `messages`, `streaming`, `loading` | `scroll-to-message` | 虚拟滚动容器 ref |
| ChatMessageBubble | `message` (单条) | `resend`, `copy`, `delete` | hover 状态 |
| ChatInput | `agentEnabled`, `ragEnabled`, `kbId` | `send`, `toggle-agent`, `toggle-rag`, `upload` | 输入文本、上传菜单开关 |
| ChatAgentPanel | `reasoningSteps` | `collapse` | 展开/折叠 |
| ChatTimelineBar | `userMessages[]` | — | hover 状态 |
| ChatScrollButton | `visible`, `streaming` | `click` | — |

#### 拆分规则

- Chat.vue 退化为纯编排器：引入子组件 + 调用 composables，不含业务逻辑
- composables 抽离有状态逻辑（`useChat.js` 从 Chat.vue `<script>` 搬出 ~800 行 SSE 逻辑）
- 保持现有功能完全不变，纯重构

---

### 5.2 后端分层规范化

#### 5.2.1 UserContext — 消除重复的 findUserByEmail

**新建** `com.zora.utils.UserContext`：

```java
@Component
public class UserContext {
    // 通过 RequestContextHolder 获取当前请求的 userEmail
    // 一次查询 UserMapper，ThreadLocal 缓存 userId/email/role
    // 请求结束自动清理（Filter 或 Interceptor afterCompletion）
    public Long getUserId();
    public String getEmail();
    public String getRole();
}
```

所有 Service 从 `findUserByEmail(email).getId()` 改为 `userContext.getUserId()`。

此改动同时需要确保 `LoginInterceptor` 在填充 `request.setAttribute("userEmail", ...)` 的同时也填充 `request.setAttribute("userId", ...)` + `request.setAttribute("userRole", ...)`，让 `UserContext` 直接取 attribute 无需二次查库。

#### 5.2.2 PageResult<T> — 统一分页返回

**新建** `com.zora.entity.dto.PageResult`：

```java
public class PageResult<T> {
    private List<T> list;
    private long total;
    private int page;
    private int size;
    // getter/setter + 构造器
}
```

Service 层不再返回 `Map<String, Object>`，统一返回 `PageResult<T>`。Controller 层统一用 `ResponseUtil.success(pageResult)` 包装。

#### 5.2.3 ErrorCode 枚举 — 收拢异常码

**新建** `com.zora.exception.ErrorCode`：

```java
public enum ErrorCode {
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    RATE_LIMITED(429, "操作太频繁"),
    CONV_NOT_FOUND(404, "对话不存在"),
    KB_NOT_FOUND(404, "知识库不存在"),
    ;
}
```

`BusinessException` 构造器增加可选的 `ErrorCode` 参数（保留 String msg 构造器向后兼容）。只收拢重复抛出的异常——不一口气枚举所有错误码。

#### 5.2.4 config/ 包整理

```
config/
├── AiConfig.java
├── AgentConfig.java
├── RagConfig.java
├── SecurityConfig.java          # BCrypt
├── WebConfig.java               # CORS + 拦截器注册
├── MyConfig.java                # MyBatis-Plus 分页
├── WechatConfig.java
├── AsyncConfig.java
├── auth/                        # 认证鉴权
│   ├── LoginInterceptor.java
│   ├── RoleInterceptor.java
│   └── RequireRole.java
├── tracking/                    # 行为追踪
│   ├── TrackAction.java
│   ├── ActionLogAspect.java
│   └── ActionLogWriter.java
└── task/
    └── CleanupTask.java
```

`entity/` 包增加 `dto/` 子包：

```
entity/
├── User.java, ChatConversation.java, ...    # DB 实体不变
└── dto/
    ├── SearchResult.java                    # 从 entity/ 迁入
    ├── AgentStep.java                       # 从 entity/ 迁入
    └── PageResult.java                      # 新增
```

#### 不改的

- 不引入 DDD 分层（Controller → Service → Mapper 已够用）
- 不动 Mapper 层
- 不做文件重命名（减少无意义 diff）

---

### 5.3 多模型支持

#### 配置设计

```yaml
ai:
  providers:
    deepseek:
      type: openai-compatible
      base-url: https://api.deepseek.com/v1
      api-key: ${DEEPSEEK_API_KEY:}
      models:
        - id: deepseek-chat
          name: DeepSeek-V3
        - id: deepseek-reasoner
          name: DeepSeek-R1
    qwen:
      type: openai-compatible
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      api-key: ${QWEN_API_KEY:}
      models:
        - id: qwen-plus
          name: 通义千问 Plus
        - id: qwen-max
          name: 通义千问 Max
    openai:
      type: openai-compatible
      base-url: https://api.openai.com/v1
      api-key: ${OPENAI_API_KEY:}
      models:
        - id: gpt-4o
          name: GPT-4o
```

#### 代码改动

```
AiConfig.java:
  旧: @Bean ChatLanguageModel → 单个 DeepSeek 硬编码
  新: @Bean ModelRegistry → Map<String, ChatLanguageModel> + Map<String, StreamingChatLanguageModel>
      路由键: "deepseek:deepseek-chat"

新建 ModelRegistry.java:
  getStreamingModel(provider, modelId)
  getChatModel(provider, modelId)
  listModels() → List<ModelInfo>

新建 GET /ai/models:
  返回: [{ provider, modelId, name }]
  前端缓存，无需每次请求

AiChatServiceImpl + AgentServiceImpl:
  从注入固定模型 → 注入 ModelRegistry，根据 conversation 字段动态获取
```

#### 数据库变更

```sql
ALTER TABLE chat_conversation
    ADD COLUMN model_provider VARCHAR(32) DEFAULT 'deepseek',
    ADD COLUMN model_id       VARCHAR(64) DEFAULT 'deepseek-chat';
```

#### 前端改动

Chat.vue 顶部模型选择器：当前 "DeepSeek" badge → `el-dropdown` 下拉，列出 `GET /ai/models` 返回的模型，切换后下次发消息时传给后端。

#### 不做

- Embedding 模型切换（此轮只做对话模型）
- 模型计费/Token 统计
- 模型 fallback 链

---

### 5.4 测试加固

#### 集成测试

**新建** `springboot/src/test/java/com/zora/integration/`：

| 测试类 | 场景数 | 验证内容 |
|--------|-------|---------|
| `RagPipelineIntegrationTest` | ~5 | 文档上传 → Tika 解析 → 递归分块 → Embedding → 检索返回正确 chunks |
| `TokenFlowIntegrationTest` | ~5 | 注册 → 发双 Token → accessToken 过期刷新 → 旧 accessToken 失效 |
| `BruteForceIntegrationTest` | ~5 | 5 次失败登录 → 锁定 → Redis TTL 15min → 解锁后可登录 |

使用 Testcontainers 启动真实 MySQL 8 + Redis 7 容器。不走 mock，验证真实组件协作。

#### 依赖

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

#### 目标

| 测试层 | 当前 | 目标 |
|--------|------|------|
| 单元测试 | 403 | 403（不动） |
| 集成测试 | 0 | ~15 |

#### 不做

- Playwright E2E 测试（个人项目维护成本太高）
- AI 模型 mock 集成测试（mock 了跟单元测试无区别）
- Prompt Injection 对抗测试（已有 18 种模式在 AgentServiceImplTest 覆盖）

---

### 5.5 性能优化

#### 5.5.1 虚拟滚动

消息列表组件 `ChatMessageList.vue` 使用 `vue-virtual-scroller` 的 `RecycleScroller` 包装，只渲染可视区域 ± buffer(200px) 内的消息 DOM 节点。500 条消息的对话从 ~500 个 DOM 节点降至 ~20 个。

#### 5.5.2 SSE Token Buffer

在 `useChat.js` 的 `onToken` 回调加 60ms flush buffer，将 Vue 响应式触发频率从每秒几十次降至 ~16 次（60fps 内不可见差异）。不改后端 SSE 协议。

#### 不做

- SSE 服务端缓冲（改协议成本高）
- 连接池调优（个人规模碰不到瓶颈）
- RAG 查询 Redis 缓存（数据量小，重复查询开销可忽略）

---

### 5.6 新功能开发（后续）

以下方向待 5.1-5.5 完成后按需展开：

- 文件/图片上传（"+ "按钮功能已有，后端补实现）
- 对话分享（生成分享链接）
- Prompt 模板库（保存/复用常用提示词）
- MCP 工具生态接入
- 系统提示词自定义（按对话维度）
- 深色模式

---

## 实施顺序

1. **5.2** 后端规范化（先做 → 改动面最大，做完跑 403 测试确认无损）
2. **5.4** 集成测试（紧接着 5.2 做 → 新的 UserContext/PageResult 需要集成测试兜底）
3. **5.1** Chat.vue 拆分（前端独立，不阻塞后端）
4. **5.5** 性能优化（虚拟滚动 + SSE buffer，依赖 5.1 拆分完成）
5. **5.3** 多模型支持（依赖 5.2 规范化完成 → ModelRegistry 替换固定 Bean）

## 不做的（明确排除）

- 向量数据库切换（Milvus/Qdrant）→ 个人规模用不上
- Prometheus + Grafana 可观测性 → 企业税
- TypeScript 迁移 → Vue 3 + JS 对学习项目完全够用
- ADR / CONTRIBUTING.md → solo 项目形式大于实质
- DDD 六边形架构重写 → 过度设计

## 相关文件

| 文件 | 改动 |
|------|------|
| `web/frontend/src/views/Chat.vue` | 退化为编排器 ~300 行 |
| `web/frontend/src/components/chat/*.vue` | 7 个新组件 |
| `web/frontend/src/composables/*.js` | 4 个新 composable |
| `springboot/.../utils/UserContext.java` | 新建 |
| `springboot/.../entity/dto/PageResult.java` | 新建 |
| `springboot/.../exception/ErrorCode.java` | 新建 |
| `springboot/.../config/ModelRegistry.java` | 新建 |
| `springboot/.../config/AiConfig.java` | 修改：多模型 Bean → ModelRegistry |
| `springboot/.../controller/AiChatController.java` | 新增 GET /ai/models |
| `springboot/.../service/impl/*.java` | UserContext 替换 findUserByEmail；PageResult 替换 Map |
| `springboot/.../config/` 包结构 | 新增 auth/tracking/task 子包 |
| `springboot/.../entity/` 包结构 | 新增 dto 子包 |
| `springboot/src/test/.../integration/` | 3 个新集成测试类 |
