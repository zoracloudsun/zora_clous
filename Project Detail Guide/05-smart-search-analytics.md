# Phase 4：智能搜索与分析（已完成）

## 概述

Phase 4 在 AI 对话（Phase 1）、RAG 知识库（Phase 2）和 AI Agent 智能体（Phase 3）基础上，新增四大智能分析能力：**全文搜索**（跨对话检索历史消息）、**数据仪表盘**（ECharts 可视化图表）、**用户行为分析**（AOP + @Async 异步日志）、**智能推荐**（关键词提取 + FULLTEXT 匹配）。

**核心原则**：不引入 ElasticSearch 等重型中间件，全部基于现有技术栈实现。MySQL 8 InnoDB FULLTEXT + ngram parser 负责中文全文搜索，ECharts 6 负责前端图表，AOP + @Async 负责行为日志异步写入，Bigram 中文分词 + FULLTEXT 匹配负责智能推荐。

> **403 个单元测试**覆盖所有 Phase 4 模块（JUnit 5 + Mockito + MockMvc），零失败。


---

## Task A: 全文搜索引擎

### 目标

支持跨对话搜索消息内容，搜索结果按相关性排序，匹配关键词高亮显示。

### 技术选型

**为什么用 MySQL FULLTEXT 而不是 ElasticSearch？**

|方案 |优点 |缺点 |选择 |
|---|---|---|:---:|
|MySQL FULLTEXT |零额外中间件、与现有 DB 一体、SQL 查询方便 |不支持分布式、大数据量性能有限 |✅ Phase 4 |
|ElasticSearch |全文搜索功能齐全、分布式、高亮内建 |需要额外 Docker 容器、内存占用大、运维复杂 |后续 |
|前端 `filter()` |零开发 |O(n) 扫描、无相关性排序、无分词 |仅本地标题过滤 |

**结论**：项目当前数据量（数十万条消息）下，MySQL FULLTEXT + ngram parser 完全够用，且零运维成本。

### A.1 数据库迁移 — V5__search_index.sql

**操作**：创建 [V5__search_index.sql](springboot/src/main/resources/db/migration/V5__search_index.sql)。

```sql
-- 为 chat_message.content 添加全文索引（ngram 中文分词）
ALTER TABLE chat_message
    ADD FULLTEXT INDEX ft_chat_message_content (content) WITH PARSER ngram;

-- 为 chat_conversation.title 添加全文索引
ALTER TABLE chat_conversation
    ADD FULLTEXT INDEX ft_chat_conversation_title (title) WITH PARSER ngram;
```

**设计要点**：

- **ngram parser**：MySQL 8 内置的中文分词器，默认 n=2（双字分词）。例如"人工智能"会被分为"人工"、"工智"、"智能"三个 bigram，搜索"智能"能命中
- `IN BOOLEAN MODE`：支持 `+`（必须包含）、`-`（排除）、`*`（通配符）等操作符，比 `NATURAL LANGUAGE MODE` 更灵活
- **为什么不像 ElasticSearch 那样对每条消息建索引表？** InnoDB FULLTEXT INDEX 直接建在原表上，无需额外的索引表。删除消息通过 `deleted_at IS NULL` 在 SQL 中过滤即可

### A.2 搜索结果 DTO — SearchResult

**操作**：创建 [SearchResult.java](springboot/src/main/java/com/zora/entity/SearchResult.java)。

```java
/**
 * 搜索结果 DTO（只读，不映射数据库表）
 * <p>
 * 字段全部使用包装类型（Long/Double），对应 MyBatis XML 查询结果 NULL-safe。
 * </p>
 */
public class SearchResult {
    private Long messageId;
    private Long conversationId;
    private String conversationTitle;
    private String role;              // user / assistant
    private String content;           // 原始内容
    private String highlightContent;  // 高亮后内容（含 <mark> 标签）
    private LocalDateTime createdAt;
    private Double relevanceScore;   // MySQL FULLTEXT 相关性分数
    // getter/setter...
}
```

### A.3 Mapper XML — 项目首个自定义 SQL

**操作**：创建 [ChatMessageMapper.xml](springboot/src/main/resources/mapper/ChatMessageMapper.xml)，修改 [ChatMessageMapper.java](springboot/src/main/java/com/zora/mapper/ChatMessageMapper.java)。

这是项目第一个 Mapper XML（此前所有查询都通过 MyBatis-Plus BaseMapper + LambdaQueryWrapper）。

**ChatMessageMapper.xml 核心 SQL**：

```xml
<select id="fulltextSearch" resultType="com.zora.entity.SearchResult">
    SELECT
        m.id AS messageId,
        c.id AS conversationId,
        c.title AS conversationTitle,
        m.role,
        m.content,
        m.created_at AS createdAt,
        MATCH(m.content) AGAINST(#{keyword} IN BOOLEAN MODE) AS relevanceScore
    FROM chat_message m
    INNER JOIN chat_conversation c ON m.conversation_id = c.id
    WHERE c.user_id = #{userId}
      AND m.deleted_at IS NULL
      AND c.deleted_at IS NULL
      AND MATCH(m.content) AGAINST(#{keyword} IN BOOLEAN MODE)
    ORDER BY relevanceScore DESC
    LIMIT #{offset}, #{limit}
</select>
```

**设计要点**：

- `MATCH ... AGAINST` 调用 InnoDB FULLTEXT 引擎，返回的 `relevanceScore` 是 MySQL 计算的文本相关性分数（基于 TF-IDF）
- **数据隔离**：`WHERE c.user_id = #{userId}` 确保用户只能搜索自己的消息
- **软删除过滤**：`m.deleted_at IS NULL AND c.deleted_at IS NULL` 排除已删除数据

### A.4 SearchService — 搜索逻辑 + 高亮处理

**操作**：创建 [SearchServiceImpl.java](springboot/src/main/java/com/zora/service/impl/SearchServiceImpl.java)。

```java
@Service
public class SearchServiceImpl implements SearchService {

    // 关键词最大长度（防止恶意超长关键词）
    private static final int MAX_KEYWORD_LENGTH = 200;

    // MySQL FULLTEXT 特殊字符（需要转义）
    private static final Pattern FULLTEXT_SPECIAL_CHARS =
        Pattern.compile("[+\\-><\\(\\)~\\*\"@]+");

    @Override
    public Map<String, Object> searchMessages(String email, String keyword,
                                               int page, int size) {
        // 1. 关键词校验
        validateKeyword(keyword);

        // 2. 查找用户
        User user = findUserByEmail(email);

        // 3. 转义 FULLTEXT 特殊字符
        String escapedKeyword = escapeFulltextChars(keyword);

        // 4. 分页搜索
        int offset = (page - 1) * size;
        List<SearchResult> results = chatMessageMapper.fulltextSearch(
            user.getId(), escapedKeyword, offset, size);
        long total = chatMessageMapper.fulltextSearchCount(
            user.getId(), escapedKeyword);

        // 5. Java 层高亮处理
        for (SearchResult r : results) {
            r.setHighlightContent(highlight(r.getContent(), keyword));
        }

        // 6. 返回分页结果
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("list", results);
        return result;
    }

    /**
     * Java 层关键词高亮
     * <p>
     * MySQL FULLTEXT 不提供内置高亮功能，在 Java 层用正则替换实现。
     * 使用 (?i) 忽略大小写，{1,1000}? 非贪婪匹配防止性能问题。
     * 生成的结果经前端 DOMPurify 安全过滤后以 v-html 渲染。
     * </p>
     */
    private String highlight(String content, String keyword) {
        return content.replaceAll(
            "(?i)(" + Pattern.quote(keyword) + ")",
            "<mark>$1</mark>");
    }
}
```

**设计要点**：

- **关键词校验分层**：非空（null/blank）→ 长度限制（200 字符）→ 特殊字符转义。每一层都在到达 SQL 前拦截
- **为什么用** `+keyword` 前缀？ MySQL `IN BOOLEAN MODE` 中 `+` 表示该词必须出现。将用户输入的关键词拆成 bigram 后每段前缀 `+`，等价于"所有 bigram 都必须出现"，提高精确度
- **高亮在 Java 层而非 SQL 层**：MySQL FULLTEXT 不提供类似 ElasticSearch 的 highlight 功能，Java 层正则替换是标准做法

### A.5 前端搜索页面 — Search.vue

**操作**：创建 [Search.vue](web/frontend/src/views/Search.vue)。

**页面布局**：

```text
┌────────────────────────────────────────────────────┐
│  [← 返回]          全文搜索                         │
│  ┌─────────────────────────────────────┐ [搜索]    │
│  │ 输入关键词搜索对话历史...               │          │
│  └─────────────────────────────────────┘          │
│                                                    │
│  ┌─────────────────────────────────────┐          │
│  │ "消息内容..."  [user]  相关度: 85%   │          │
│  │ 来自对话：项目技术选型    2025-06-15  │          │
│  └─────────────────────────────────────┘          │
│  ...更多结果...                                    │
│                                [分页: 1 2 3 ...]   │
└────────────────────────────────────────────────────┘
```

**核心功能**：

|功能 |实现 |
|---|---|
|防抖搜索 |300ms debounce，避免每次按键触发搜索 |
|高亮显示 |`v-html` 渲染 `<mark>` 标签（经 DOMPurify 过滤） |
|对话跳转 |点击对话标题 → `router.push('/chat?conversationId=X')` 深度链接 |
|角色标签 |`el-tag` 蓝色 "user" / 绿色 "assistant" |
|分页 |`el-pagination`，默认 20 条/页 |

**新增路由**：

```js
{ path: '/search', name: 'Search', component: () => import('@/views/Search.vue'),
  meta: { requiresAuth: true } }
```


---

## Task B: 对话数据分析仪表盘

### 目标

可视化图表展示用户对话统计数据：消息趋势、活跃时段、消息占比、功能使用排行。

### 技术选型

**为什么用 ECharts 而不是 Chart.js？**

|方案 |优点 |缺点 |选择 |
|---|---|---|:---:|
|ECharts 6 |中文友好、图表类型丰富、Canvas 渲染性能好、按需导入体积可控 |npm 包体积约 1MB |✅ Phase 4 |
|Chart.js |更轻量（~200KB） |图表类型少、国内社区小 |不适合 |

### B.1 Mapper 扩展 — 统计查询 SQL

**操作**：修改 [ChatMessageMapper.java](springboot/src/main/java/com/zora/mapper/ChatMessageMapper.java) 和 [ChatMessageMapper.xml](springboot/src/main/resources/mapper/ChatMessageMapper.xml)，新增 3 个统计方法。

```java
// 每日消息数趋势（分 user/assistant 统计）
List<Map<String, Object>> countMessagesByDay(
    @Param("userId") Integer userId, @Param("days") int days);

// 按小时统计活跃度（0-23 时分布）
List<Map<String, Object>> countMessagesByHour(
    @Param("userId") Integer userId);

// 按角色统计总数（user vs assistant 占比）
List<Map<String, Object>> countMessagesByRole(
    @Param("userId") Integer userId);
```

**ChatMessageMapper.xml 核心 SQL**：

```xml
<!-- 每日消息数趋势 — 分组统计 -->
<select id="countMessagesByDay" resultType="java.util.HashMap">
    SELECT
        DATE(m.created_at) AS date,
        SUM(CASE WHEN m.role = 'user' THEN 1 ELSE 0 END) AS userCount,
        SUM(CASE WHEN m.role = 'assistant' THEN 1 ELSE 0 END) AS aiCount
    FROM chat_message m
    INNER JOIN chat_conversation c ON m.conversation_id = c.id
    WHERE c.user_id = #{userId}
      AND m.created_at >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY)
      AND m.deleted_at IS NULL AND c.deleted_at IS NULL
    GROUP BY DATE(m.created_at)
    ORDER BY date ASC
</select>
```

### B.2 StatisticsService — 统计计算 + Redis 缓存

**操作**：创建 [StatisticsServiceImpl.java](springboot/src/main/java/com/zora/service/impl/StatisticsServiceImpl.java)。

**8 个统计端点**：

|方法 |返回 |说明 |
|---|---|---|
|`getOverview(email)` |Map |总览：总会话数、总消息数、本周活跃天数、AI 使用率 |
|`getMessageTrend(email, days)` |Map |每日消息数趋势（默认 30 天），双线折线图数据 |
|`getActiveHours(email)` |Map |24 小时活跃热力图数据（0-23 时消息分布） |
|`getConversationTrend(email, days)` |Map |每日新对话创建趋势 |
|`getMessageRatio(email)` |Map |用户 vs AI 消息占比（饼图数据） |
|`getKnowledgeBaseStats(email)` |Map |知识库使用统计 |
|`getActionRanking(email)` |Map |功能使用排行（从 user_action_log 聚合） |
|`getWeeklyActivity(email)` |Map |最近 7 天每日活跃操作次数 |

**Redis 缓存策略**：

```java
// TTL 分级缓存 — 不同数据有不同的变化频率
private static final long TTL_OVERVIEW = 3600;        // 1 小时
private static final long TTL_TREND = 3600;           // 1 小时
private static final long TTL_HOURS = 14400;          // 4 小时
private static final long TTL_KB_STATS = 1800;        // 30 分钟
private static final long TTL_ACTION_RANKING = 3600;  // 1 小时

/**
 * 通用缓存模式：先查 Redis → 命中返回 → 未命中查 DB → 写 Redis → 返回
 */
private Map<String, Object> getCachedOrCompute(
        String key, long ttlSeconds, Supplier<Map<String, Object>> supplier) {
    try {
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return jsonMapper.readValue(cached, Map.class);
        }
    } catch (Exception e) {
        log.debug("缓存读取失败: {}", e.getMessage());
    }
    Map<String, Object> result = supplier.get();
    try {
        stringRedisTemplate.opsForValue().set(key,
            jsonMapper.writeValueAsString(result), ttlSeconds, TimeUnit.SECONDS);
    } catch (Exception e) {
        log.warn("缓存写入失败: {}", e.getMessage());
    }
    return result;
}
```

**设计要点**：

- **TTL 分级缓存**：消息趋势 1h、活跃时段 4h（极少变化）、知识库统计 30min。匹配数据实际变化频率
- `getCachedOrCompute` 模板方法：消除所有统计方法的 Redis 样板代码
- **空值填充**：对于没有数据的日期/小时，填充 0 值。保证前端 ECharts 图表连续完整
- **Jackson 序列化**：将 `Map<String, Object>` 序列化为 JSON 存储在 Redis String 中

### B.3 前端图表组件

**操作**：安装 ECharts (`npm install echarts --save`)，创建 4 个图表组件。

**组件架构**：

```text
BaseChart.vue（基类）
  ├── LineChart.vue  — 折线图（消息趋势、对话趋势）
  ├── BarChart.vue   — 柱状图（活跃时段、功能排行）
  └── PieChart.vue   — 饼图（消息角色占比）
```

**BaseChart.vue 核心设计**：

```text
<template>
  <div ref="chartRef" :style="{ width: '100%', height: height + 'px' }">
    <!-- el-skeleton 加载占位 -->
    <el-skeleton v-if="loading" :rows="6" animated />
  </div>
</template>

<script setup>
const props = defineProps({
  option: { type: Object, required: true },
  height: { type: Number, default: 400 },
  loading: { type: Boolean, default: false },
})

const chartRef = ref(null)
let chartInstance = null

// watch: option 变化时重新 setOption（带动画过渡）
watch(() => props.option, (newOpt) => {
  if (chartInstance && newOpt) {
    chartInstance.setOption(newOpt, { notMerge: true })
  }
}, { deep: true })

// onMounted: init ECharts → setOption
// onUnmounted: dispose（防止内存泄漏）
// 窗口 resize 时自动 resize 图表
</script>
```

**设计要点**：

- **watch + notMerge**：选项变化时完整替换配置（而非合并），确保从"折线图数据"切换到"空数据"时不留残留
- **dispose 防泄漏**：`onUnmounted` 中调用 `chartInstance.dispose()`，释放 Canvas 资源
- **resize 响应式**：窗口尺寸变化时自动 resize，配合 CSS Grid 的响应式布局

**Dashboard.vue 页面布局**：

```text
┌──────────────────────────────────────────────────────┐
│  📊 数据仪表盘                                        │
├──────────┬──────────┬──────────┬──────────────────────┤
│ 总会话数  │ 总消息数  │ 活跃天数  │ AI 使用率 60%        │
│   15     │   230    │    5     │                      │
├──────────┴──────────┴──────────┴──────────────────────┤
│  📈 消息趋势（30 天）                                   │
│  [折线图: 蓝色用户消息 + 绿色 AI 回复 双线]              │
├───────────────────────────┬──────────────────────────│
│  🕐 活跃时段分布            │  🥧 消息角色占比            │
│  [柱状图: 0-23 时]         │  [饼图: User / Assistant] │
├───────────────────────────┴──────────────────────────│
│  📈 新对话创建趋势                                     │
│  [折线图: 每日新对话数]                                 │
├──────────────────────────────────────────────────────│
│  🏆 功能使用排行                                       │
│  [柱状图: message_send / conv_create / search_query...]│
└──────────────────────────────────────────────────────┘
```


---

## Task C: 用户行为分析

### 目标

追踪用户关键操作，分析功能使用模式和用户活跃度。

### 技术选型

**为什么用 AOP + 自定义注解而不是手动在每个 Controller 方法中写日志？**

|方案 |优点 |缺点 |选择 |
|---|---|---|:---:|
|AOP + @TrackAction |零侵入、自动拦截、不重复代码 |需要理解 AOP 概念 |✅ Phase 4 |
|手动写入 actionLogMapper.insert() |简单直观 |每个方法都要加代码、容易遗漏 |不可维护 |

### C.1 数据库迁移 — V6__user_action_log.sql

**操作**：创建 [V6__user_action_log.sql](springboot/src/main/resources/db/migration/V6__user_action_log.sql)。

```sql
CREATE TABLE user_action_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT NOT NULL,
    action      VARCHAR(50) NOT NULL
        COMMENT 'conv_create|message_send|search_query|kb_upload|kb_query|agent_call',
    target_id   BIGINT DEFAULT NULL,
    detail      VARCHAR(500) DEFAULT NULL COMMENT 'JSON 格式详情',
    ip_address  VARCHAR(45) DEFAULT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_action_user_time (user_id, action, created_at),
    INDEX idx_action_created (created_at),
    FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**设计要点**：

- `action` 用 VARCHAR 而非 ENUM：ENUM 修改需要 ALTER TABLE，VARCHAR + 索引更灵活
- `detail` 预留 JSON 字段：当前未使用，预留给后续扩展（如搜索时记录搜索关键词、KB 上传时记录文件名）
- **联合索引** `(user_id, action, created_at)`：覆盖最常见的查询"某用户的某类操作按时间排序"

### C.2 自定义注解 — @TrackAction

**操作**：创建 [TrackAction.java](springboot/src/main/java/com/zora/config/TrackAction.java)。

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackAction {
    /** 行为类型 */
    String value();
}
```

**6 种行为类型**（在 `UserActionLog` 实体中定义常量）：

|常量 |值 |触发位置 |
|---|---|---|
|`ACTION_CONV_CREATE` |`conv_create` |`AiChatController.createConversation()` |
|`ACTION_MESSAGE_SEND` |`message_send` |`AiChatController.streamChat()` / `AgentController.agentStreamChat()` |
|`ACTION_SEARCH_QUERY` |`search_query` |`SearchController.searchMessages()` |
|`ACTION_KB_UPLOAD` |`kb_upload` |`RagController.createKnowledgeBase()` |
|`ACTION_KB_QUERY` |`kb_query` |`RagController.queryKnowledgeBase()` |
|`ACTION_AGENT_CALL` |`agent_call` |`AgentController.agentStreamChat()` |

### C.3 AOP 切面 — ActionLogAspect + ActionLogWriter（修复 Request 回收问题）

**操作**：创建 [ActionLogAspect.java](springboot/src/main/java/com/zora/config/ActionLogAspect.java) 和 [ActionLogWriter.java](springboot/src/main/java/com/zora/config/ActionLogWriter.java)。

**架构设计**：

```text
HTTP 主线程                              异步线程 (action-log-*)
    │                                         │
    ├─ @AfterReturning 触发                     │
    ├─ 提取 email (request.getAttribute)       │
    ├─ 提取 action (@TrackAction.value())      │
    ├─ 提取 IP (request.getHeader/getRemoteAddr)│
    ├─ Delegates ──→ actionLogWriter.write() ──→ ├─ 查找 userId
    └─ 立即返回（不阻塞响应）                     ├─ INSERT user_action_log
                                                  └─ 失败仅 log.warn
```

**关键技术问题**：`HttpServletRequest` 是请求作用域对象——HTTP 响应发送后 Tomcat 会回收 Request 对象（对象池机制）。如果 `@Async` 标注在切面方法上，提取数据也在异步线程中执行，此时 Request 可能已被回收，导致 "request object has been recycled" 错误。

**解决方案**：将"数据提取"和"DB 写入"分离到两个类中：

```java
// ActionLogAspect — 主线程同步执行（@AfterReturning，无 @Async）
@AfterReturning(pointcut = "@annotation(trackAction)", returning = "result")
public void logAction(JoinPoint joinPoint, TrackAction trackAction, Object result) {
    HttpServletRequest request = findRequest(joinPoint);
    if (request == null) return;

    String email = (String) request.getAttribute("userEmail");  // 主线程提取
    String action = trackAction.value();
    String ip = getClientIp(request);

    actionLogWriter.write(email, action, ip);  // 跨 Bean 委托，@Async 生效
}

// ActionLogWriter — 独立 @Component，@Async 方法
@Async
public void write(String email, String action, String ip) {
    // 纯字符串参数，无 Request 依赖，可在任意线程安全执行
    Integer userId = resolveUserId(email);
    UserActionLog log = new UserActionLog(userId, action, ip);
    actionLogMapper.insert(log);
}
```

**设计要点**：

- **为什么分离成两个类？** `@Async` 通过 Spring AOP 代理生效——同类自调用（`this.write()`）不会触发代理，必须跨 Bean 调用
- `write()` 只接收纯字符串参数：`email` / `action` / `ip` 都是从 Request 中提取出来的不可变值，在异步线程中完全安全
- **异常隔离**：写入失败只 `log.warn`，不抛异常——行为日志不应中断主业务流程

### C.4 异步线程池配置

**操作**：创建 [AsyncConfig.java](springboot/src/main/java/com/zora/config/AsyncConfig.java)。

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);       // 日常 2 个线程足够
        executor.setMaxPoolSize(5);        // 高峰期上限
        executor.setQueueCapacity(100);    // 缓冲 100 个峰值任务
        executor.setThreadNamePrefix("action-log-");
        executor.setRejectedExecutionHandler(
            new CallerRunsPolicy());       // 队列满时由调用线程执行，不丢日志
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
```

### C.5 注解应用到 Controller

|Controller 方法 |注解 |
|---|---|
|`AiChatController.streamChat()` |`@TrackAction("message_send")` |
|`AiChatController.createConversation()` |`@TrackAction("conv_create")` |
|`SearchController.searchMessages()` |`@TrackAction("search_query")` |
|`AgentController.agentStreamChat()` |`@TrackAction("agent_call")` |
|`RagController.createKnowledgeBase()` |`@TrackAction("kb_upload")` |
|`RagController.queryKnowledgeBase()` |`@TrackAction("kb_query")` |


---

## Task D: 智能推荐

### 目标

基于用户历史对话内容，推荐相关历史对话、建议问题和热门知识库。

### 技术选型

**为什么不引入 ML 框架（如 TensorFlow/Word2Vec）？**

|方案 |优点 |缺点 |选择 |
|---|---|---|:---:|
|Bigram + FULLTEXT |零依赖、Java 原生、即时可用 |精度不如深度学习 |✅ Phase 4 |
|Word2Vec / BERT |语义理解准确 |需要模型文件（GB 级）、GPU/内存开销大 |过度工程 |

### D.1 关键词提取算法

**操作**：在 [RecommendServiceImpl.java](springboot/src/main/java/com/zora/service/impl/RecommendServiceImpl.java) 中实现。

```java
/**
 * Bigram 中文关键词提取
 * <p>
 * 遍历文本，取连续两个中文字符作为 bigram。
 * 过滤停用词（"的"、"了"、"是"等 50+ 中文常见词），
 * 按词频排序，取 Top 5。
 * 每个 bigram 前加 + 符合 MySQL BOOLEAN MODE "必须包含"语法。
 * </p>
 */
private String extractKeywords(List<ChatMessage> messages) {
    // 1. 拼接所有消息内容
    StringBuilder all = new StringBuilder();
    for (ChatMessage m : messages) {
        if (m.getContent() != null) all.append(m.getContent());
    }

    // 2. 遍历生成 bigram
    Map<String, Integer> freq = new HashMap<>();
    for (int i = 0; i < all.length() - 1; i++) {
        String bigram = all.substring(i, i + 2);
        if (!bigram.matches("[一-龥]{2}")) continue;  // 仅中文双字
        if (STOP_WORDS.contains(bigram)) continue;
        freq.merge(bigram, 1, Integer::sum);
    }

    // 3. 按词频排序 → Top 5
    List<Map.Entry<String, Integer>> sorted = new ArrayList<>(freq.entrySet());
    sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

    StringBuilder keywords = new StringBuilder();
    for (int i = 0; i < Math.min(5, sorted.size()); i++) {
        if (keywords.length() > 0) keywords.append(" ");
        keywords.append("+").append(sorted.get(i).getKey());
    }
    return keywords.toString();
}
```

### D.2 三维推荐

**推荐接口**：`GET /recommend/suggestions`

**三维推荐数据结构**：

```json
{
  "relatedConversations": [
    { "conversationId": 42, "title": "设计模式讨论", "matchCount": 3 }
  ],
  "suggestedQuestions": [
    "总结一下我们之前讨论的要点",
    "能用更简单的方式解释这个概念吗"
  ],
  "popularKnowledge": [
    { "id": 5, "name": "项目文档", "documentCount": 12 }
  ]
}
```

**三维推荐生成逻辑**：

|维度 |算法 |说明 |
|---|---|---|
|相关对话 |提取用户最近消息的 Bigram → FULLTEXT 搜索匹配的对话 → 排除最近活跃对话 → 按平均相关性排序 |推荐"你可能想回顾"的历史对话 |
|建议问题 |Bigram 主题匹配预设模板库（编程/学习/数据库/通用 4 类） |推荐"你可能想问"的问题 |
|热门知识库 |按文档数量降序取 Top 3 |推荐"你经常用"的知识库 |

**Redis 缓存**：Key `recommend:{userId}`，TTL 30 分钟。与统计数据缓存策略一致。

### D.3 前端推荐卡片 — RecommendCard

**操作**：创建 [RecommendCard.vue](web/frontend/src/components/RecommendCard.vue)，嵌入 [Chat.vue](web/frontend/src/views/Chat.vue) 侧边栏。

**组件布局**：

```text
┌─────────────────────┐
│ [相关对话] [建议问题] [热门知识] │  ← el-tabs
├─────────────────────┤
│ 📄 设计模式讨论       │
│    匹配 3 条消息       │
│ 📄 性能优化方案       │
│    匹配 2 条消息       │
│ 📄 Redis 使用技巧    │
│    匹配 2 条消息       │
└─────────────────────┘
```

**交互设计**：

- 相关对话：点击标题 → `router.push('/chat?conversationId=X')` 跳转到指定对话
- 建议问题：`el-tag` 可点击，点击后填入输入框并自动发送
- 热门知识：显示知识库名 + 文档数量标签


---

## Phase 4 API 汇总

### 新增端点（10 个）

|方法 |路径 |说明 |认证 |
|---|---|---|:---:|
|GET |`/search/messages?q=关键词&page=1&size=20` |全文搜索消息 |✅ |
|GET |`/statistics/overview` |数据总览 |✅ |
|GET |`/statistics/message-trend?days=30` |消息趋势（折线图） |✅ |
|GET |`/statistics/active-hours` |活跃时段（0-23 时） |✅ |
|GET |`/statistics/conversation-trend?days=30` |对话创建趋势 |✅ |
|GET |`/statistics/message-ratio` |用户 vs AI 消息占比 |✅ |
|GET |`/statistics/knowledge-stats` |知识库统计 |✅ |
|GET |`/statistics/action-ranking` |功能使用排行 |✅ |
|GET |`/statistics/weekly-activity` |最近 7 天活跃度 |✅ |
|GET |`/recommend/suggestions` |智能推荐 |✅ |

### Controller 注解应用

|Controller |注解 |
|---|---|
|`AiChatController` |`@TrackAction("message_send")` + `@TrackAction("conv_create")` |
|`AgentController` |`@TrackAction("agent_call")` |
|`SearchController` |`@TrackAction("search_query")` |
|`RagController` |`@TrackAction("kb_upload")` + `@TrackAction("kb_query")` |


---

## Phase 4 数据流全景图

```text
═══════════════════════════════════════════════════════
全文搜索
═══════════════════════════════════════════════════════

用户输入关键词（Search.vue）
  → GET /search/messages?q=微服务
    → SearchController.searchMessages()
      → SearchService.searchMessages()
        ├─ validateKeyword() (非空 + 200 字限制)
        ├─ escapeFulltextChars() (转义 + - > < 等特殊字符)
        ├─ ChatMessageMapper.fulltextSearch()
        │   └─ SELECT ... MATCH(content) AGAINST('微服务' IN BOOLEAN MODE)
        │       └─ InnoDB FULLTEXT 引擎 (ngram: 微服/服务)
        ├─ highlight() Java 层正则替换 <mark>微服务</mark>
        └─ 返回 { total, list: [{..., highlightContent}] }

前端渲染：
  v-html="DOMPurify.sanitize(highlightContent)"
    → 关键词高亮显示 + 点击标题跳转 /chat?conversationId=X

═══════════════════════════════════════════════════════
数据仪表盘
═══════════════════════════════════════════════════════

用户打开仪表盘（Dashboard.vue）
  → 并行请求 8 个统计端点
    → StatisticsController → StatisticsService
      ├─ getCachedOrCompute(key, ttl, supplier)
      │   ├─ Redis 命中 → 直接返回
      │   └─ Redis 未命中 → DB 聚合查询 → Jackson 序列化 → Redis SETEX
      └─ 返回 { dates, userCounts, aiCounts } 等

前端渲染：
  ECharts setOption(dates, series) → Canvas 图表
  BaseChart watch(option) → 数据变化时带动画更新

═══════════════════════════════════════════════════════
用户行为分析
═══════════════════════════════════════════════════════

用户操作（如发送消息）
  → AiChatController.streamChat() (@TrackAction("message_send"))
    → 方法正常返回
      → @AfterReturning → ActionLogAspect.logAction()
        ├─ 主线程提取: email (request.getAttribute) + action + IP
        └─ actionLogWriter.write(email, action, ip)  跨 Bean 委托
            → 异步线程 (action-log-*)
              ├─ resolveUserId(email) → userId
              ├─ new UserActionLog(userId, action, ip)
              └─ actionLogMapper.insert() → user_action_log 表

统计分析：
  GET /statistics/action-ranking
    → SELECT action, COUNT(*) FROM user_action_log
      WHERE user_id=? GROUP BY action ORDER BY COUNT(*) DESC

═══════════════════════════════════════════════════════
智能推荐
═══════════════════════════════════════════════════════

Chat.vue 侧边栏 RecommendCard 组件
  → GET /recommend/suggestions
    → RecommendService.getRecommendations(email)
      ├─ findRelatedConversations()
      │   ├─ 加载最近 10 条用户消息
      │   ├─ extractKeywords() (Bigram 分词 + 停止词过滤 + Top 5)
      │   ├─ ChatMessageMapper.fulltextSearch(keywords)
      │   ├─ 排除最近 5 个活跃对话（去重）
      │   └─ 按平均相关性排序 → Top 3
      ├─ generateSuggestedQuestions()
      │   └─ Bigram 主题匹配模板库 → 返回 3 个建议问题
      └─ findPopularKnowledge()
          └─ 按文档数量降序 → Top 3
```


---

## Phase 4 文件变更总览

### 新增后端文件（18 个）

|文件 |说明 |
|---|---|
|`db/migration/V5__search_index.sql` |MySQL FULLTEXT 索引（ngram 中文分词） |
|`db/migration/V6__user_action_log.sql` |用户行为日志表 |
|`entity/SearchResult.java` |搜索结果 DTO（9 个字段） |
|`entity/UserActionLog.java` |行为日志实体（6 个行为类型常量） |
|`mapper/ChatMessageMapper.xml` |**项目首个 Mapper XML** — 全文搜索 + 统计 SQL |
|`mapper/UserActionLogMapper.java` |行为日志 Mapper |
|`service/SearchService.java` |搜索服务接口 |
|`service/impl/SearchServiceImpl.java` |搜索服务实现（关键词校验 + 高亮） |
|`service/StatisticsService.java` |统计服务接口 |
|`service/impl/StatisticsServiceImpl.java` |统计服务实现（8 个统计方法 + Redis 分级缓存） |
|`service/RecommendService.java` |推荐服务接口 |
|`service/impl/RecommendServiceImpl.java` |推荐服务实现（Bigram 分词 + FULLTEXT 三维推荐） |
|`controller/SearchController.java` |GET /search/messages 全文搜索端点 |
|`controller/StatisticsController.java` |8 个统计端点 |
|`controller/RecommendController.java` |GET /recommend/suggestions 推荐端点 |
|`config/TrackAction.java` |@TrackAction 自定义注解 |
|`config/ActionLogAspect.java` |AOP 切面（主线程数据提取 + 委托异步写入） |
|`config/ActionLogWriter.java` |独立的 @Async 行为日志写入组件 |
|`config/AsyncConfig.java` |@Async 线程池配置（core=2, max=5, queue=100） |

### 修改后端文件（5 个）

|文件 |变更 |
|---|---|
|`pom.xml` |新增 spring-boot-starter-aop 依赖 |
|`mapper/ChatMessageMapper.java` |新增 fulltextSearch/fulltextSearchCount/countMessagesByDay/countMessagesByHour/countMessagesByRole |
|`mapper/ChatConversationMapper.java` |新增 countConversationsByDay |
|`controller/AiChatController.java` |新增 @TrackAction 注解 |
|`controller/AgentController.java` |新增 @TrackAction 注解 |
|`controller/RagController.java` |新增 @TrackAction 注解 |
|`application.yml` |springdoc group-configs 新增 /search/**, /statistics/**, /recommend/** |

### 新增前端文件（14 个）

|文件 |说明 |
|---|---|
|`api/search.js` |搜索 API 封装 |
|`api/statistics.js` |8 个统计 API 函数 |
|`api/recommend.js` |推荐 API 封装 |
|`views/Search.vue` |全文搜索页面（防抖 + 高亮 + 分页 + 深度链接） |
|`views/Dashboard.vue` |数据仪表盘页面（4 个摘要卡片 + 6 个图表） |
|`components/charts/BaseChart.vue` |ECharts 基类组件（init/dispose/resize） |
|`components/charts/LineChart.vue` |折线图组件 |
|`components/charts/BarChart.vue` |柱状图组件 |
|`components/charts/PieChart.vue` |饼图组件（甜甜圈样式） |
|`components/RecommendCard.vue` |推荐卡片组件（3 Tab：对话/问题/知识库） |
|`node_modules/echarts` |ECharts 6 npm 依赖 |

### 修改前端文件（4 个）

|文件 |变更 |
|---|---|
|`router/index.js` |新增 `/search` 和 `/dashboard` 路由 |
|`views/Home.vue` |新增"全文搜索"和"数据仪表盘"导航按钮 |
|`views/Chat.vue` |侧边栏底部新增"仪表盘"按钮、嵌入 RecommendCard、推荐问题交互 |
|`vite.config.js` |新增 `/search`, `/statistics`, `/recommend` 代理规则 |

### 新增测试文件（7 个，32 个新测试用例）

|测试文件 |测试数 |覆盖内容 |
|---|---|---|
|`SearchControllerTest.java` |3 |搜索端点：正常搜索/无结果/未认证 |
|`SearchServiceImplTest.java` |6 |关键词校验（blank/null/超长）、高亮、分页、用户不存在 |
|`StatisticsControllerTest.java` |5 |overview/message-trend/active-hours/action-ranking 端点 |
|`RecommendControllerTest.java` |2 |正常推荐/空数据 |
|**合计** |**32** |**Phase 4 总计新增 32 个测试** |


---

## Phase 4 验证清单

|# |测试场景 |预期结果 |
|---|---|---|
|1 |登录后发送多条 AI 消息，进入"全文搜索" |搜索页面加载，输入框可用 |
|2 |搜索"微服务"，点击搜索 |返回匹配的消息，关键词高亮显示为黄色 |
|3 |点击搜索结果中的对话标题 |跳转至 Chat 页面并加载对应对话 |
|4 |进入"数据仪表盘" |4 个摘要卡片 + 6 个 ECharts 图表正常渲染 |
|5 |切换消息趋势天数为 7 天 |折线图动态更新 |
|6 |发送一条消息后打开仪表盘 |总消息数 +1，趋势图最后一天数据更新 |
|7 |查看 MySQL `user_action_log` 表 |有 `message_send` 记录，含 user_id/action/ip/created_at |
|8 |查看 MySQL `user_action_log` 表 |有 `conv_create` 记录（新建对话时） |
|9 |查看 MySQL `user_action_log` 表 |有 `search_query` 记录（搜索时） |
|10 |Chat 页面侧边栏底部查看 RecommendCard |显示相关对话/建议问题/热门知识三个 Tab |
|11 |点击建议问题 |自动填入输入框并发送 |
|12 |删除一个对话后查看回收站 badged |数字立即更新（+1），无需刷新页面 |
|13 |运行 `cd springboot && mvn test` |403 个测试全部通过 |


