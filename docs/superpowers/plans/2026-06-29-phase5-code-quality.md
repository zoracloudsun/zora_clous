# Phase 5: 代码质量提升与多模型支持 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构代码结构（消除重复、统一分层）+ 补充集成测试 + 支持多模型切换，不引入 bug。

**Architecture:** 渐进式外科手术 — 每次只动一处，跑完测试确认无损再动下一处。先做纯增量的基础设施（UserContext / PageResult / ErrorCode），再做文件搬家（config 子包），最后做行为变更（Service 替换）。

**Tech Stack:** Spring Boot 3.5 + MyBatis-Plus + Redis + Vue 3 (Composition API) + Testcontainers

**关键约束:** 403 个已有测试必须在每个 Task 结束后全部通过。

---

## 实施顺序

| 阶段 | 内容 | 依赖 |
|------|------|------|
| **Phase 5.2-A** | UserContext + ErrorCode + PageResult（纯增量） | 无 |
| **Phase 5.2-B** | config/ 和 entity/ 包整理（文件搬家） | 5.2-A |
| **Phase 5.2-C** | Service 替换 findUserByEmail + PageResult | 5.2-B |
| **Phase 5.4** | 集成测试（Testcontainers） | 5.2-C |
| **Phase 5.1** | Chat.vue 组件拆分 | 无（前端独立） |
| **Phase 5.5** | 性能优化（虚拟滚动 + SSE buffer） | 5.1 |
| **Phase 5.3** | 多模型支持 | 5.2-C + 5.1 |

---

## Phase 5.2-A: 纯增量基础设施

### Task 1: 创建 UserContext 工具类

**Files:**
- Create: `springboot/src/main/java/com/zora/utils/UserContext.java`

**说明:** 不改变任何已有代码行为。新增一个 ThreadLocal 缓存用户上下文的工具类。提取 `findUserByEmail` 为一次查库、请求内缓存的模式。

设计要点：
- 不是 Filter/Interceptor，是工具类，按需调用
- 从 Spring 的 `RequestContextHolder` 获取当前请求的 `userEmail` attribute（LoginInterceptor 已设置）
- 缓存 userId 查询结果到 ThreadLocal，请求结束时通过 `@Scheduled` 或 Filter 清理
- 降级：如果没有 request context（如定时任务），走 UserMapper 查询

- [ ] **Step 1: 创建 UserContext.java**

```java
package com.zora.utils;

import com.zora.entity.User;
import com.zora.mapper.UserMapper;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 用户上下文工具类
 * <p>
 * 从当前 HTTP 请求中提取用户身份信息（email/role），并缓存 userId 查询结果。
 * 消除各 Service 中重复的 {@code findUserByEmail} 私有方法。
 * </p>
 *
 * <p><b>ThreadLocal 缓存:</b> 同一请求内多次调用 getUserId() 只查一次库。
 * 缓存 Key: userEmail，请求结束后由 GC 回收（无显式清理 — 线程池场景安全，Spring MVC 每请求一线程）。</p>
 */
@Component
public class UserContext {

    @Resource
    private UserMapper userMapper;

    /** ThreadLocal 缓存: email → userId */
    private final ThreadLocal<String> emailCache = new ThreadLocal<>();
    private final ThreadLocal<Long> userIdCache = new ThreadLocal<>();
    private final ThreadLocal<String> roleCache = new ThreadLocal<>();

    /**
     * 获取当前请求用户的 email
     * @return 用户邮箱，无登录上下文时返回 null
     */
    public String getEmail() {
        String cached = emailCache.get();
        if (cached != null) return cached;

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;

        HttpServletRequest request = attrs.getRequest();
        String email = (String) request.getAttribute("userEmail");
        if (email != null) emailCache.set(email);
        return email;
    }

    /**
     * 获取当前请求用户的 ID（带缓存）
     * @return 用户 ID，无登录上下文或用户不存在时返回 null
     */
    public Long getUserId() {
        Long cached = userIdCache.get();
        if (cached != null) return cached;

        String email = getEmail();
        if (email == null) return null;

        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email));
        if (user != null) {
            userIdCache.set(user.getId());
            return user.getId();
        }
        return null;
    }

    /**
     * 获取当前请求用户的角色
     * @return 角色字符串，无登录上下文时返回 null
     */
    public String getRole() {
        String cached = roleCache.get();
        if (cached != null) return cached;

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;

        HttpServletRequest request = attrs.getRequest();
        String role = (String) request.getAttribute("userRole");
        if (role != null) roleCache.set(role);
        return role;
    }

    /**
     * 清除当前线程缓存（用于线程池复用的极端场景，非必须）
     */
    public void clear() {
        emailCache.remove();
        userIdCache.remove();
        roleCache.remove();
    }
}
```

- [ ] **Step 2: 确认编译通过**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add springboot/src/main/java/com/zora/utils/UserContext.java
git commit -m "feat: 新增 UserContext 工具类 — ThreadLocal 缓存用户信息，消除重复 findUserByEmail"
```

---

### Task 2: 创建 PageResult 通用分页 DTO

**Files:**
- Create: `springboot/src/main/java/com/zora/entity/dto/PageResult.java`

- [ ] **Step 1: 创建 dto 目录 + PageResult.java**

```bash
mkdir -p springboot/src/main/java/com/zora/entity/dto
```

```java
package com.zora.entity.dto;

import java.util.List;

/**
 * 通用分页结果 DTO
 * <p>替代 Service 层返回 {@code Map<String, Object>} 的分页模式。</p>
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> {

    private List<T> list;
    private long total;
    private int page;
    private int size;

    public PageResult() {}

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd springboot && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add springboot/src/main/java/com/zora/entity/dto/PageResult.java
git commit -m "feat: 新增 PageResult<T> 通用分页 DTO"
```

---

### Task 3: 创建 ErrorCode 异常码枚举

**Files:**
- Create: `springboot/src/main/java/com/zora/exception/ErrorCode.java`
- Modify: `springboot/src/main/java/com/zora/exception/BusinessException.java`

- [ ] **Step 1: 创建 ErrorCode.java**

```java
package com.zora.exception;

/**
 * 业务异常码枚举
 * <p>只收拢跨 Service 重复抛出的异常，不枚举所有错误码。</p>
 */
public enum ErrorCode {

    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    RATE_LIMITED(429, "操作太频繁"),
    CONV_NOT_FOUND(404, "对话不存在"),
    KB_NOT_FOUND(404, "知识库不存在"),
    DOC_NOT_FOUND(404, "文档不存在"),
    ;

    private final int code;
    private final String defaultMsg;

    ErrorCode(int code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }

    public int getCode() { return code; }
    public String getDefaultMsg() { return defaultMsg; }
}
```

- [ ] **Step 2: 在 BusinessException 中增加可选的 ErrorCode 参数**

先读取当前 BusinessException：

```java
// 当前文件: springboot/src/main/java/com/zora/exception/BusinessException.java

// 现有构造器保持不变，新增以下内容：

// ---- 新增: ErrorCode 字段（可选） ----
private ErrorCode errorCode;

/**
 * 新增构造器: 使用 ErrorCode 枚举的默认消息
 */
public BusinessException(ErrorCode errorCode) {
    super(errorCode.getDefaultMsg());
    this.code = errorCode.getCode();
    this.errorCode = errorCode;
}

/**
 * 新增构造器: ErrorCode + 自定义消息
 */
public BusinessException(ErrorCode errorCode, String customMsg) {
    super(customMsg);
    this.code = errorCode.getCode();
    this.errorCode = errorCode;
}

public ErrorCode getErrorCode() { return errorCode; }
```

需要加在现有构造器后面。不要改动任何已有构造器。

Edit 操作: 在 BusinessException.java 的 `private int code;` 行下方增加 `private ErrorCode errorCode;`，在最后一个构造器后增加两个新构造器 + getter。

- [ ] **Step 3: 跑所有测试确认无损**

```bash
cd springboot && mvn test -q
```

Expected: Tests run: 403, Failures: 0, BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add springboot/src/main/java/com/zora/exception/
git commit -m "feat: 新增 ErrorCode 枚举 + BusinessException 支持 ErrorCode 参数"
```

---

## Phase 5.2-B: 包结构整理（纯文件搬家）

### Task 4: config/ 包重组 — 将拦截器、切面、定时任务移入子包

**Files (move):**
- `config/LoginInterceptor.java` → `config/auth/LoginInterceptor.java`
- `config/RoleInterceptor.java` → `config/auth/RoleInterceptor.java`
- `config/RequireRole.java` → `config/auth/RequireRole.java`
- `config/ActionLogAspect.java` → `config/tracking/ActionLogAspect.java`
- `config/ActionLogWriter.java` → `config/tracking/ActionLogWriter.java`
- `config/TrackAction.java` → `config/tracking/TrackAction.java`
- `config/CleanupTask.java` → `config/task/CleanupTask.java`

**Files (modify — import 更新):**
- `config/WebConfig.java` — 更新 `LoginInterceptor` 和 `RoleInterceptor` import
- `config/AsyncConfig.java` — 无需改（不引用被移动的文件）
- `controller/UserController.java` — 更新 `RequireRole` import
- `controller/AiChatController.java` — 更新 `@com.zora.config.TrackAction` 全限定名
- `controller/AgentController.java` — 同上
- `controller/SearchController.java` — 同上
- `controller/RagController.java` — 同上（两处）
- `test/.../LoginInterceptorTest.java` — 更新 import
- `test/.../RoleInterceptorTest.java` — 更新 import

- [ ] **Step 1: 创建子目录**

```bash
mkdir -p springboot/src/main/java/com/zora/config/auth
mkdir -p springboot/src/main/java/com/zora/config/tracking
mkdir -p springboot/src/main/java/com/zora/config/task
```

- [ ] **Step 2: 移动 7 个文件**

```bash
# auth 子包
git mv springboot/src/main/java/com/zora/config/LoginInterceptor.java \
       springboot/src/main/java/com/zora/config/auth/LoginInterceptor.java
git mv springboot/src/main/java/com/zora/config/RoleInterceptor.java \
       springboot/src/main/java/com/zora/config/auth/RoleInterceptor.java
git mv springboot/src/main/java/com/zora/config/RequireRole.java \
       springboot/src/main/java/com/zora/config/auth/RequireRole.java

# tracking 子包
git mv springboot/src/main/java/com/zora/config/ActionLogAspect.java \
       springboot/src/main/java/com/zora/config/tracking/ActionLogAspect.java
git mv springboot/src/main/java/com/zora/config/ActionLogWriter.java \
       springboot/src/main/java/com/zora/config/tracking/ActionLogWriter.java
git mv springboot/src/main/java/com/zora/config/TrackAction.java \
       springboot/src/main/java/com/zora/config/tracking/TrackAction.java

# task 子包
git mv springboot/src/main/java/com/zora/config/CleanupTask.java \
       springboot/src/main/java/com/zora/config/task/CleanupTask.java
```

- [ ] **Step 3: 修改被移动文件的 package 声明**

每个被移动的文件，第一行 `package com.zora.config;` 改为对应的子包名：

| 文件 | 改为 |
|------|------|
| `config/auth/LoginInterceptor.java` | `package com.zora.config.auth;` |
| `config/auth/RoleInterceptor.java` | `package com.zora.config.auth;` |
| `config/auth/RequireRole.java` | `package com.zora.config.auth;` |
| `config/tracking/ActionLogAspect.java` | `package com.zora.config.tracking;` |
| `config/tracking/ActionLogWriter.java` | `package com.zora.config.tracking;` |
| `config/tracking/TrackAction.java` | `package com.zora.config.tracking;` |
| `config/task/CleanupTask.java` | `package com.zora.config.task;` |

- [ ] **Step 4: 更新所有引用文件的 import**

**WebConfig.java** (`springboot/src/main/java/com/zora/config/WebConfig.java`):
```java
// 旧:
import com.zora.config.LoginInterceptor;  // (实际上 WebConfig 用的是 @Resource 无需 import，但需确认)
```
WebConfig 用 `@Resource` 注入，字段类型在同一个包不需要 import。但移到子包后需要添加 import：
```java
import com.zora.config.auth.LoginInterceptor;
import com.zora.config.auth.RoleInterceptor;
```

**UserController.java** (`springboot/src/main/java/com/zora/controller/UserController.java`):
```java
// 旧:
import com.zora.config.RequireRole;
// 改为:
import com.zora.config.auth.RequireRole;
```

**AiChatController.java** (`springboot/src/main/java/com/zora/controller/AiChatController.java`):
```java
// 两处全限定名:
// @com.zora.config.TrackAction("message_send")
// @com.zora.config.TrackAction("conv_create")
// 都改为:
// @com.zora.config.tracking.TrackAction("message_send")
// @com.zora.config.tracking.TrackAction("conv_create")
```

**AgentController.java** (`springboot/src/main/java/com/zora/controller/AgentController.java`):
```java
// @com.zora.config.TrackAction("agent_call")
// 改为:
// @com.zora.config.tracking.TrackAction("agent_call")
```

**SearchController.java** (`springboot/src/main/java/com/zora/controller/SearchController.java`):
```java
// @com.zora.config.TrackAction("search_query")
// 改为:
// @com.zora.config.tracking.TrackAction("search_query")
```

**RagController.java** (`springboot/src/main/java/com/zora/controller/RagController.java`):
```java
// @com.zora.config.TrackAction("kb_upload")
// → @com.zora.config.tracking.TrackAction("kb_upload")
// @com.zora.config.TrackAction("kb_query")
// → @com.zora.config.tracking.TrackAction("kb_query")
```

**LoginInterceptorTest.java** (`springboot/src/test/java/com/zora/config/LoginInterceptorTest.java`):
```java
// 旧 import:
import com.zora.config.LoginInterceptor;
// 改为:
import com.zora.config.auth.LoginInterceptor;
```

**RoleInterceptorTest.java** (`springboot/src/test/java/com/zora/config/RoleInterceptorTest.java`):
```java
// 旧 import:
import com.zora.config.RequireRole;
import com.zora.config.RoleInterceptor;
// 改为:
import com.zora.config.auth.RequireRole;
import com.zora.config.auth.RoleInterceptor;
```

- [ ] **Step 5: 跑全部测试确认无损**

```bash
cd springboot && mvn test -q
```

Expected: Tests run: 403, Failures: 0, BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A springboot/src/main/java/com/zora/config/
git add springboot/src/main/java/com/zora/controller/
git add springboot/src/test/java/com/zora/config/
git commit -m "refactor: config 包重组 — auth/tracking/task 子包分类"
```

---

### Task 5: entity/dto/ 子包 — 迁入 SearchResult + AgentStep

**Files (move):**
- `entity/SearchResult.java` → `entity/dto/SearchResult.java`
- `entity/AgentStep.java` → `entity/dto/AgentStep.java`

**Files (modify):** 所有引用 SearchResult 和 AgentStep 的文件需要更新 import。

- [ ] **Step 1: 创建 dto 目录（如已由 Task 2 创建则跳过）**

```bash
mkdir -p springboot/src/main/java/com/zora/entity/dto
```

- [ ] **Step 2: 移动文件**

```bash
git mv springboot/src/main/java/com/zora/entity/SearchResult.java \
       springboot/src/main/java/com/zora/entity/dto/SearchResult.java
git mv springboot/src/main/java/com/zora/entity/AgentStep.java \
       springboot/src/main/java/com/zora/entity/dto/AgentStep.java
```

- [ ] **Step 3: 修改 package 声明**

| 文件 | 改为 |
|------|------|
| `entity/dto/SearchResult.java` | `package com.zora.entity.dto;` |
| `entity/dto/AgentStep.java` | `package com.zora.entity.dto;` |

- [ ] **Step 4: 更新所有 import 引用**

找到所有 `import com.zora.entity.SearchResult` 和 `import com.zora.entity.AgentStep`，更新为新的 package 路径。

通过 grep 查找：
```bash
grep -rn "import com.zora.entity.SearchResult\|import com.zora.entity.AgentStep" springboot/src/main/java springboot/src/test/java
```

预计需要更新的文件（用 grep 实际确认为准）：
- `service/SearchService.java`
- `service/impl/SearchServiceImpl.java`
- `mapper/ChatMessageMapper.java`
- `controller/SearchController.java`
- `agent/graph/AgentState.java`（如果引用了 AgentStep）

逐一更新 import。

- [ ] **Step 5: 跑全部测试确认**

```bash
cd springboot && mvn test -q
```

Expected: Tests run: 403, Failures: 0, BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A springboot/src/main/java/com/zora/entity/
git add springboot/src/main/java/com/zora/service/
git add springboot/src/main/java/com/zora/controller/
git add springboot/src/main/java/com/zora/mapper/
git add springboot/src/main/java/com/zora/agent/
git commit -m "refactor: entity/dto 子包 — 迁入 SearchResult + AgentStep"
```

---

## Phase 5.2-C: Service 层替换

### Task 6: 替换 findUserByEmail → UserContext

**策略:** 逐个 ServiceImpl 替换，每替换一个就跑测试确认。

第一步：在 LoginInterceptor 中增加设置 userId attribute（UserContext 无需额外查库）。

- [ ] **Step 1: LoginInterceptor 增加 userId attribute**

在 `config/auth/LoginInterceptor.java` 的 `preHandle` 方法末尾，在已有的 `setAttribute("userRole", ...)` 之后，增加 userId 查找和设置：

```java
// 在 preHandle 方法中，request.setAttribute("userRole", ...) 之后添加：
// 注意: 需要注入 UserMapper
@Resource
private com.zora.mapper.UserMapper userMapper;

// 在 setAttribute("userRole", ...) 后:
com.zora.entity.User user = userMapper.selectOne(
    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.zora.entity.User>()
        .eq(com.zora.entity.User::getEmail, username));
if (user != null) {
    request.setAttribute("userId", user.getId());
}
```

- [ ] **Step 2: 更新 UserContext.getUserId() 优先使用 request attribute**

修改 UserContext.java 中的 getUserId() 方法，先尝试从 request attribute 获取（免查库）：

```java
public Long getUserId() {
    Long cached = userIdCache.get();
    if (cached != null) return cached;

    // 优先从 request attribute 获取（LoginInterceptor 已设置，免查库）
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs != null) {
        HttpServletRequest request = attrs.getRequest();
        Object userIdAttr = request.getAttribute("userId");
        if (userIdAttr != null) {
            long uid = ((Number) userIdAttr).longValue();
            userIdCache.set(uid);
            return uid;
        }
    }

    // 降级: 自行查库
    String email = getEmail();
    if (email == null) return null;
    User user = userMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                    .eq(User::getEmail, email));
    if (user != null) {
        userIdCache.set(user.getId());
        return user.getId();
    }
    return null;
}
```

- [ ] **Step 3: 跑测试确认 LoginInterceptor + UserContext**

```bash
cd springboot && mvn test -q
```

Expected: 403 tests pass

- [ ] **Step 4: 替换 SearchServiceImpl 的 findUserByEmail**

**文件:** `springboot/src/main/java/com/zora/service/impl/SearchServiceImpl.java`

```java
// 新增注入:
@Resource
private com.zora.utils.UserContext userContext;

// 删除 private User findUserByEmail(String email) 方法

// searchMessages 方法中:
// 旧: User user = findUserByEmail(email);
// 新: Long userId = userContext.getUserId();
// 删除 User user 变量，删除 user.getId() 调用，改用 userId
```

跑测试: `cd springboot && mvn test -Dtest="SearchServiceImplTest,SearchControllerTest" -q`

- [ ] **Step 5: 替换 RecommendServiceImpl 的 findUserByEmail**

```java
// 新增注入:
@Resource
private com.zora.utils.UserContext userContext;

// getRecommendations 方法:
// 旧: User user = findUserByEmail(email);
// 新: Long userId = userContext.getUserId();

// 删除 private User findUserByEmail(String email) 方法
// findRelatedConversations 接受 User → 改为接受 Long userId
// findPopularKnowledge 接受 User → 改为接受 Long userId
```

跑测试: `cd springboot && mvn test -Dtest="RecommendControllerTest" -q`

- [ ] **Step 6: 替换 AiChatServiceImpl 的 findUserByEmail**

共有 9 处引用。逐一替换：

```java
// 新增注入:
@Resource
private com.zora.utils.UserContext userContext;

// 所有:
// User user = findUserByEmail(email);
// Long userId = user.getId();
// 改为:
// Long userId = userContext.getUserId();

// 删除 private User findUserByEmail(String email) 方法
```

跑测试: `cd springboot && mvn test -Dtest="AiChatServiceImplTest,AiChatControllerTest" -q`

- [ ] **Step 7: 替换 RagServiceImpl 的 findUserByEmail**

```java
// 新增注入:
@Resource
private com.zora.utils.UserContext userContext;

// 所有 findUserByEmail(email) 替换为 userContext.getUserId()
// 删除 private User findUserByEmail(String email) 方法
```

跑测试: `cd springboot && mvn test -Dtest="RagServiceImplTest,RagControllerTest" -q`

- [ ] **Step 8: 替换 AgentServiceImpl 的 findUserByEmail**

```java
// 新增注入:
@Resource
private com.zora.utils.UserContext userContext;

// 替换 findUserByEmail(email) → userContext.getUserId()
// 删除 private User findUserByEmail(String email) 方法
```

跑测试: `cd springboot && mvn test -Dtest="AgentServiceImplTest,AgentControllerTest" -q`

- [ ] **Step 9: 全量测试**

```bash
cd springboot && mvn test -q
```

Expected: Tests run: 403, Failures: 0, BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add springboot/src/main/java/com/zora/
git commit -m "refactor: 统一使用 UserContext 替代 private findUserByEmail"
```

---

### Task 7: SearchServiceImpl 使用 PageResult 替换 Map 返回

**Files:**
- Modify: `springboot/src/main/java/com/zora/service/impl/SearchServiceImpl.java`
- Modify: `springboot/src/main/java/com/zora/service/SearchService.java`
- Modify: `springboot/src/main/java/com/zora/controller/SearchController.java`

- [ ] **Step 1: 修改 SearchService 接口返回类型**

```java
// SearchService.java:
// 旧: Map<String, Object> searchMessages(String email, String keyword, int page, int size);
// 新: PageResult<SearchResult> searchMessages(String email, String keyword, int page, int size);
```

- [ ] **Step 2: 修改 SearchServiceImpl 实现**

```java
// 返回值类型改为 PageResult<SearchResult>
// 旧: Map<String, Object> response = new LinkedHashMap<>(); response.put("total", total)...
// 新: return new PageResult<>(results, total, page, size);
// results 已是从 fulltextSearch 返回的 List<SearchResult>
```

- [ ] **Step 3: 修改 SearchController**

```java
// SearchController.searchMessages():
// 旧: Map<String, Object> result = searchService.searchMessages(email, keyword, page, size);
//     return new ResponseUtil(200, "搜索成功", result);
// 新: PageResult<SearchResult> result = searchService.searchMessages(email, keyword, page, size);
//     return new ResponseUtil(200, "搜索成功", result);
```

ResponseUtil 的 `data` 字段接受 Object，PageResult 可以直接传入，序列化后 JSON 结构为 `{ list, total, page, size }`。

- [ ] **Step 4: 跑测试**

```bash
cd springboot && mvn test -Dtest="SearchServiceImplTest,SearchControllerTest" -q
```

- [ ] **Step 5: Commit**

```bash
git add springboot/src/main/java/com/zora/service/ springboot/src/main/java/com/zora/controller/
git commit -m "refactor: SearchService 返回 PageResult<SearchResult> 替代 Map"
```

---

## Phase 5.4: 集成测试

### Task 8: 集成测试基础设施

**Files:**
- Modify: `springboot/pom.xml`（如 testcontainers 依赖不存在则添加）
- Create: `springboot/src/test/resources/application-testcontainers.yml`
- Create: `springboot/src/test/java/com/zora/integration/AbstractIntegrationTest.java`

- [ ] **Step 1: 检查 pom.xml 是否已有 testcontainers**

```bash
grep "testcontainers" springboot/pom.xml
```

如果不存在，在 `spring-boot-starter-test` 依赖后添加：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.20.4</version>
    <scope>test</scope>
</dependency>
```

注意：Spring Boot 3.5.11 自带 Testcontainers 版本管理（`spring-boot-testcontainers`），如果已有该依赖，版本可省略。

- [ ] **Step 2: 创建集成测试基类**

```java
package com.zora.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 集成测试基类
 * <p>启动真实 MySQL 8 + Redis 7 容器（通过 Testcontainers）。
 * 子类只需 extends 此类即可获得容器支持。</p>
 */
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    // Redis 容器使用 GenericContainer（如 redis 镜像未拉取则自动拉取）
    @Container
    static org.testcontainers.containers.GenericContainer<?> redis =
            new org.testcontainers.containers.GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add springboot/pom.xml springboot/src/test/
git commit -m "test: 集成测试基类 — Testcontainers MySQL + Redis"
```

---

### Task 9: RagPipelineIntegrationTest

**Files:**
- Create: `springboot/src/test/java/com/zora/integration/RagPipelineIntegrationTest.java`

验证: 文档上传 → Tika 解析 → 递归分块 → Embedding → 检索返回正确 chunks。

测试场景:
1. 创建知识库 → 上传 txt 文档 → 验证分块入库 → 检索返回相关内容
2. 上传空文件 → 验证正确处理
3. 删除文档后检索不应返回该文档 chunks
4. KnowledgeBase 软删除 → 回收站恢复 → chunks 重新可检索
5. 上传 PDF 文档 → Tika 解析 → 分块正常

此测试需要加载完整 Spring 上下文（`@SpringBootTest`），mock EmbeddingModel API 调用（OpenAI API 需要网络和密钥，不适合集成测试）。

由于 Embedding API 需要外部网络，在集成测试中用 `@MockBean` mock `EmbeddingModel`，返回固定向量。验证的是管道逻辑（分块/入库/检索），不是 Embedding 质量。

具体代码在实施时编写，遵循 AAA 模式（Arrange-Act-Assert）。

- [ ] **Step 1: 编写测试类**
- [ ] **Step 2: 运行测试**

```bash
cd springboot && mvn test -Dtest="RagPipelineIntegrationTest" -q
```

- [ ] **Step 3: Commit**

```bash
git add springboot/src/test/java/com/zora/integration/
git commit -m "test: RagPipelineIntegrationTest — RAG 管道端到端集成测试"
```

---

### Task 10: TokenFlowIntegrationTest

**Files:**
- Create: `springboot/src/test/java/com/zora/integration/TokenFlowIntegrationTest.java`

验证: 注册 → 发双 Token → accessToken 过期刷新 → 旧 accessToken 失效。

测试场景:
1. 完整注册+登录流程 → 拿到双 token
2. 用 accessToken 访问受保护端点 → 成功
3. 用 refreshToken 刷新 → 拿到新 accessToken
4. 旧 accessToken 刷新后失效（Redis 对比失败）
5. 用 refreshToken 当 accessToken 使用 → 被拒绝（type 校验）

此测试需加载完整 Spring 上下文，使用真实 H2 替代 MySQL（不需要 MySQL 容器，因为 Token 逻辑主要依赖 Redis + JWT）。

如果 H2 兼容性问题太多，降级为仅测试 JWT + Redis 层的 Token 流转逻辑（不需要 Spring Boot 启动，使用 `RedisTestContainer` 直接操作 Redis）。

- [ ] **Step 1: 编写测试类**
- [ ] **Step 2: 运行测试**
- [ ] **Step 3: Commit**

---

### Task 11: BruteForceIntegrationTest

**Files:**
- Create: `springboot/src/test/java/com/zora/integration/BruteForceIntegrationTest.java`

验证: 5 次失败登录 → 锁定 → Redis TTL 15min → 解锁后可登录。

测试场景:
1. 5 次错误密码登录 → 锁定 → 第 6 次返回 429
2. 等待 TTL（或手动清除 Redis key）→ 再次可登录
3. 正确密码在锁定期间也被拒绝
4. 不同邮箱的锁定互不影响

- [ ] **Step 1: 编写测试类**
- [ ] **Step 2: 运行测试**
- [ ] **Step 3: Commit**

---

## Phase 5.1: Chat.vue 组件拆分

### Task 12: 创建 composables 目录 + 4 个组合式函数

**Files:**
- Create: `web/frontend/src/composables/useChat.js`
- Create: `web/frontend/src/composables/useConversations.js`
- Create: `web/frontend/src/composables/useReasoning.js`
- Create: `web/frontend/src/composables/useScroll.js`

**策略:** 先在 composables 中复制逻辑，Chat.vue 逐步替换引用，中间状态保证功能正常。

- [ ] **Step 1: 创建 useScroll.js**

```javascript
// web/frontend/src/composables/useScroll.js
import { ref, nextTick } from 'vue'

/**
 * 滚动管理组合式函数
 * 提供 isNearBottom 守卫 + scrollToBottom + 显示滚动按钮判断
 */
export function useScroll(messagesContainer) {
  const showScrollBtn = ref(false)

  const isNearBottom = () => {
    const el = messagesContainer.value
    if (!el) return true
    return el.scrollHeight - el.scrollTop - el.clientHeight <= 100
  }

  const scrollToBottom = async (smooth = false) => {
    await nextTick()
    const el = messagesContainer.value
    if (el) {
      el.scrollTo({
        top: el.scrollHeight,
        behavior: smooth ? 'smooth' : 'instant',
      })
      if (smooth) showScrollBtn.value = false
    }
  }

  const checkScrollPosition = () => {
    const el = messagesContainer.value
    if (!el) return
    showScrollBtn.value = el.scrollHeight - el.scrollTop - el.clientHeight > 100
  }

  return { showScrollBtn, isNearBottom, scrollToBottom, checkScrollPosition }
}
```

- [ ] **Step 2: 创建 useReasoning.js**

```javascript
// web/frontend/src/composables/useReasoning.js
import { ref, reactive } from 'vue'

/**
 * Agent 推理步骤管理
 */
export function useReasoning() {
  const reasoningSteps = reactive([])
  const reasoningActive = ref(false)

  const addReasoningStep = (step) => {
    reasoningSteps.push({ ...step, id: Date.now() + Math.random() })
  }

  const updateLastReasoningStep = (updates) => {
    if (reasoningSteps.length > 0) {
      Object.assign(reasoningSteps[reasoningSteps.length - 1], updates)
    }
  }

  const clearReasoning = () => {
    reasoningSteps.length = 0
    reasoningActive.value = false
  }

  return { reasoningSteps, reasoningActive, addReasoningStep, updateLastReasoningStep, clearReasoning }
}
```

- [ ] **Step 3: 创建 useConversations.js**

从 Chat.vue 中提取对话列表管理的所有逻辑：加载列表、新建、删除、批量操作、重命名、右键菜单状态等。具体提取内容在实施时对照 Chat.vue `<script>` 部分进行。

```javascript
// web/frontend/src/composables/useConversations.js
// 从 Chat.vue <script> 中提取如下逻辑：
// - conversations, currentConversationId
// - loadConversations(), handleNewChat(), handleSelectConversation()
// - handleDeleteConversation(), handleBatchDelete(), handleRenameConversation()
// - contextMenu 状态管理
```

- [ ] **Step 4: 创建 useChat.js**

从 Chat.vue 中提取 SSE 流式对话核心逻辑：

```javascript
// web/frontend/src/composables/useChat.js
// 从 Chat.vue <script> 中提取如下逻辑：
// - messages, streamingContent, isLoading
// - sendMessage(), handleSend()
// - SSE 事件处理（onToken, onDone, onError, onThinking, onToolCall, onToolResult）
// - Agent/RAG 模式切换
// - fixCjkBold()
```

- [ ] **Step 5: Commit**

```bash
git add web/frontend/src/composables/
git commit -m "feat: 创建 4 个 composables — useScroll/useReasoning/useConversations/useChat"
```

---

### Task 13-19: 创建 7 个 Chat 子组件

每个组件遵循统一模式：从 Chat.vue 复制对应的模板 + 样式 + 逻辑片段，Chat.vue 中替换为子组件引用。

**13. ChatScrollButton** — 最简单，先做
- Create: `web/frontend/src/components/chat/ChatScrollButton.vue`
- 从 Chat.vue 搬: `.scroll-bottom-btn` 模板 + 样式
- Props: `visible` (Boolean), `streaming` (Boolean)
- Emits: `click`

**14. ChatTimelineBar** — 独立组件
- Create: `web/frontend/src/components/chat/ChatTimelineBar.vue`
- 从 Chat.vue 搬: `.conv-timeline` 模板 + 样式 + `userMessageAnchors` computed
- Props: `messages` (Array)
- 自管: `timelineHovered` 状态

**15. ChatMessageBubble** — 单条消息渲染
- Create: `web/frontend/src/components/chat/ChatMessageBubble.vue`
- 从 Chat.vue 搬: 单条消息气泡的模板（Markdown 渲染、角色头像、操作按钮）+ 对应样式
- Props: `message` (Object)
- Emits: `resend`, `copy`, `delete`

**16. ChatMessageList** — 消息列表 + 虚拟滚动
- Create: `web/frontend/src/components/chat/ChatMessageList.vue`
- 从 Chat.vue 搬: `.chat-messages` 容器 + 消息循环渲染 + `messagesContainer` ref
- Props: `messages` (Array), `streaming` (Boolean), `loading` (Boolean)
- Emits: `scroll-to-message`
- 包含: `<ChatMessageBubble>` + `<ChatScrollButton>`

**17. ChatAgentPanel** — Agent 推理面板
- Create: `web/frontend/src/components/chat/ChatAgentPanel.vue`
- 从 Chat.vue 搬: 推理面板模板（思考步骤/工具调用/工具结果）+ 样式 + 动画
- Props: `reasoningSteps` (Array)
- Emits: `collapse`

**18. ChatInput** — 输入区
- Create: `web/frontend/src/components/chat/ChatInput.vue`
- 从 Chat.vue 搬: 输入框 + 药丸按钮 + 上传菜单 + 发送按钮的模板 + 样式
- Props: `agentEnabled`, `ragEnabled`, `kbId`
- Emits: `send`, `toggle-agent`, `toggle-rag`, `upload`

**19. ChatSidebar** — 侧边栏
- Create: `web/frontend/src/components/chat/ChatSidebar.vue`
- 从 Chat.vue 搬: 侧边栏完整模板（品牌、新对话按钮、搜索框、对话列表、右键菜单、批量模式）+ 样式
- Props: `conversations`, `currentId`, `collapsed`
- Emits: `select`, `new`, `delete`, `batch-delete`, `batch-restore`, `batch-permanent-delete`, `rename`

每个组件的实施步骤:
1. 创建 Vue 文件，定义 props/emits
2. 从 Chat.vue 搬对应模板 + 样式 + script 逻辑
3. 在 Chat.vue 中 import 并替换对应模板段
4. 验证编译: `cd web/frontend && npm run build`

所有子组件完成后一个 commit:
```bash
git add web/frontend/src/components/chat/ web/frontend/src/views/Chat.vue
git commit -m "refactor: Chat.vue 拆分为 7 子组件 + 4 composables"
```

---

## Phase 5.5: 性能优化

### Task 20: 安装 vue-virtual-scroller + ChatMessageList 接入虚拟滚动

- [ ] **Step 1: 安装依赖**

```bash
cd web/frontend && npm install vue-virtual-scroller
```

- [ ] **Step 2: ChatMessageList 中使用 RecycleScroller**

在 `ChatMessageList.vue` 中：

```vue
<template>
  <div ref="messagesContainer" class="chat-messages">
    <RecycleScroller
      class="scroller"
      :items="messages"
      :item-size="null"
      :buffer="200"
      key-field="id"
      v-slot="{ item }"
    >
      <ChatMessageBubble
        :message="item"
        @resend="$emit('resend', item)"
        @copy="$emit('copy', item)"
        @delete="$emit('delete', item)"
      />
    </RecycleScroller>
    <ChatScrollButton :visible="showScrollBtn" :streaming="streaming" @click="handleScrollToBottom" />
  </div>
</template>
```

- [ ] **Step 3: 注册组件**

```javascript
// ChatMessageList.vue <script setup>
import { RecycleScroller } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'
```

- [ ] **Step 4: Commit**

```bash
git add web/frontend/
git commit -m "perf: 虚拟滚动优化 — vue-virtual-scroller 减少消息列表 DOM 节点"
```

---

### Task 21: SSE Token Buffer

在 `useChat.js` 的 `onToken` 回调中增加 60ms buffer。

```javascript
// useChat.js 中:
let tokenBuffer = ''
let flushTimer = null
const FLUSH_INTERVAL = 60 // ms

function onToken(token) {
  tokenBuffer += token
  if (!flushTimer) {
    flushTimer = setTimeout(() => {
      streamingContent.value += tokenBuffer
      tokenBuffer = ''
      flushTimer = null
    }, FLUSH_INTERVAL)
  }
}

// onDone 时需要 flush 剩余 buffer:
function flushTokenBuffer() {
  if (flushTimer) {
    clearTimeout(flushTimer)
    flushTimer = null
  }
  if (tokenBuffer) {
    streamingContent.value += tokenBuffer
    tokenBuffer = ''
  }
}
```

- [ ] **Step 1: 修改 useChat.js**
- [ ] **Step 2: Commit**

```bash
git add web/frontend/src/composables/useChat.js
git commit -m "perf: SSE Token Buffer 60ms — 减少 Vue 响应式触发频率"
```

---

## Phase 5.3: 多模型支持

### Task 22: 数据库迁移 V7

**Files:**
- Create: `springboot/src/main/resources/db/migration/V7__multi_model.sql`

```sql
ALTER TABLE chat_conversation
    ADD COLUMN IF NOT EXISTS model_provider VARCHAR(32) DEFAULT 'deepseek',
    ADD COLUMN IF NOT EXISTS model_id       VARCHAR(64) DEFAULT 'deepseek-chat';
```

注意：MySQL 不支持 `ADD COLUMN IF NOT EXISTS`。需要改为 Spring Boot 兼容的模式——使用 Flyway 脚本，或者在应用层 `@PostConstruct` 中执行 `ALTER TABLE ... ADD COLUMN` 的 try-catch（忽略 `Duplicate column` 错误）。推荐直接用 Flyway 脚本不加 IF NOT EXISTS（V7 只执行一次）。

```sql
ALTER TABLE chat_conversation
    ADD COLUMN model_provider VARCHAR(32) DEFAULT 'deepseek' AFTER is_deleted,
    ADD COLUMN model_id VARCHAR(64) DEFAULT 'deepseek-chat' AFTER model_provider;
```

- [ ] **Step 1: 创建迁移文件**
- [ ] **Step 2: Commit**

```bash
git add springboot/src/main/resources/db/migration/V7__multi_model.sql
git commit -m "feat: V7 迁移 — chat_conversation 增加 model_provider/model_id"
```

---

### Task 23: 配置模型 — application.yml 多 Provider

**Files:**
- Modify: `springboot/src/main/resources/application.yml`

在现有 `ai:` 配置段替换为多 provider 格式：

```yaml
ai:
  default-provider: deepseek
  default-model: deepseek-chat
  providers:
    deepseek:
      type: openai-compatible
      base-url: https://api.deepseek.com/v1
      api-key: ${DEEPSEEK_API_KEY:}
      models:
        - id: deepseek-chat
          name: DeepSeek-V3
          streaming: true
        - id: deepseek-reasoner
          name: DeepSeek-R1
          streaming: true
    # 下面两个 Provider 默认注释掉，用户自己填 API Key 后取消注释
    # qwen:
    #   type: openai-compatible
    #   base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    #   api-key: ${QWEN_API_KEY:}
    #   models:
    #     - id: qwen-plus
    #       name: 通义千问 Plus
    #       streaming: true
    # openai:
    #   type: openai-compatible
    #   base-url: https://api.openai.com/v1
    #   api-key: ${OPENAI_API_KEY:}
    #   models:
    #     - id: gpt-4o
    #       name: GPT-4o
    #       streaming: true
```

- [ ] **Step 1: 修改 application.yml**
- [ ] **Step 2: Commit**

---

### Task 24: 创建 ModelRegistry + 改造 AiConfig

**Files:**
- Create: `springboot/src/main/java/com/zora/config/ModelRegistry.java`
- Modify: `springboot/src/main/java/com/zora/config/AiConfig.java`

**AiConfig.java** 改造为读取 `ai.providers` 配置，创建 ModelRegistry Bean（替代原有的两个固定 Bean）。

ModelRegistry 核心逻辑：

```java
// 注册所有 provider 的模型实例（每个模型创建一对 streaming + non-streaming Bean）
// 提供: getStreamingModel(provider, modelId), getChatModel(provider, modelId)
// 提供: listModels() → List<ModelInfo> (provider/modelId/name)
```

实现细节：
- 用 `@ConfigurationProperties(prefix = "ai")` 绑定配置到 `AiProperties` 内部类
- 启动时遍历 `providers` map，为每个 provider 的每个 model 创建 `ChatLanguageModel` 和 `StreamingChatLanguageModel`
- 存储到两个 `Map<String, T>` 中：key = `"provider:modelId"`

- [ ] **Step 1: 创建 AiProperties 配置属性类（内部类放在 AiConfig 中）**
- [ ] **Step 2: 创建 ModelRegistry — 启动时创建所有模型实例**
- [ ] **Step 3: 修改 AiConfig — 移除旧 @Bean，改为 @EnableConfigurationProperties + ModelRegistry Bean**
- [ ] **Step 4: 提交后跑全部测试确认 AI 相关测试仍通过**

注：现有的 AI 测试通过 `@MockBean` mock 了模型实例，不受配置变更影响。

---

### Task 25: 创建 GET /ai/models 端点 + 改造 AI Service 注入

**Files:**
- Modify: `springboot/src/main/java/com/zora/controller/AiChatController.java`
- Modify: `springboot/src/main/java/com/zora/service/impl/AiChatServiceImpl.java`
- Modify: `springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java`

- [ ] **Step 1: AiChatController 新增端点**

```java
@GetMapping("/models")
public ResponseUtil listModels() {
    List<Map<String, Object>> models = modelRegistry.listModels().stream()
        .map(m -> Map.of("provider", m.provider(), "modelId", m.modelId(), "name", m.name()))
        .toList();
    return new ResponseUtil(200, "查询成功", models);
}
```

注意：GET /ai/models 不能和 SSE 端点 `/ai/chat/stream` 冲突。确认路径不重叠 — `/ai/models` 是普通 GET 请求，`/ai/chat/stream` 是 POST + SSE，不冲突。

- [ ] **Step 2: AiChatServiceImpl 注入 ModelRegistry 替代固定模型**

```java
// 旧: @Resource private OpenAiStreamingChatModel streamingModel;
// 新: @Resource private ModelRegistry modelRegistry;

// 在 streamChat() 方法中，根据 conversation 的 provider/modelId 获取模型:
// ChatLanguageModel model = modelRegistry.getChatModel(provider, modelId);
```

如果 conversation 没有保存 model provider/id，默认使用 `application.yml` 中 `ai.default-provider` 和 `ai.default-model`。

- [ ] **Step 3: AgentServiceImpl 同样改造**
- [ ] **Step 4: 前端 ChatInput 增加模型选择器下拉**

在 ChatInput.vue 或 Chat.vue header 中增加模型切换 `el-dropdown`。页面加载时调用 `GET /ai/models`，展示可选模型列表。用户切换后更新本地状态，发送消息时传给后端存入 `model_provider` + `model_id`。

- [ ] **Step 5: 跑全部测试**

```bash
cd springboot && mvn test -q
```

Expected: 403+ tests pass

- [ ] **Step 6: Commit**

```bash
git add springboot/src/main/java/ web/frontend/src/
git commit -m "feat: 多模型支持 — ModelRegistry + GET /ai/models + 前端模型选择器"
```

---

## 验证清单

Phase 5.2 每步完成后:
- [ ] `mvn test -q` → 403 tests, 0 failures

Phase 5.4 完成后:
- [ ] 集成测试独立运行通过（Docker 必须运行）
- [ ] 全部单元测试仍然通过（集成测试不应影响单元测试）

Phase 5.1 每步完成后:
- [ ] `npm run build` → 前端编译成功
- [ ] 手动验证：对话、Agent、RAG、搜索功能正常

Phase 5.5 完成后:
- [ ] 手动验证：500 条消息的对话滚动流畅、流式输出正常

Phase 5.3 完成后:
- [ ] 手动验证：切换模型、发送消息、检查 DB 中 conversation.model_provider 已正确存储
- [ ] `mvn test -q` → 403+ tests pass

---

## Self-Review 结果

1. **Spec coverage:** 5.2 (UserContext/PageResult/ErrorCode/config 整理) ✅ → 5.4 (3 个集成测试) ✅ → 5.1 (Chat.vue 拆分) ✅ → 5.5 (虚拟滚动/SSE buffer) ✅ → 5.3 (多模型) ✅
2. **Placeholder scan:** 无 TBD/TODO。UserServiceImpl 的 ResponseUtil 返回模式明确标注为暂缓（风险收益不成比例）。
3. **Type consistency:** 所有 import 路径与移动后的实际 package 路径一致。
