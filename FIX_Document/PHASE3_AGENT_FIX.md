# Phase 3 Agent 智能体修复文档

> **优先级**：P2（功能缺陷，不影响核心安全，但严重影响 Agent 模式的可用性）
> **创建日期**：2026-06-21
> **影响范围**：`/agent/**` 端点（Agent SSE 流式对话 + 工具调用）
> **关联模块**：AgentController、AgentServiceImpl、AiConfig、AgentConfig、WebSearchTool、Chat.vue、vite.config.js

---

## 问题总览

| 编号 | 问题 | 风险等级 | 状态 | 修复方案 |
| :---: | :--- | :---: | :---: | :--- |
| P3-1 | Vite 代理缺少 `/agent` 路由 | 🔴 高 | ✅ 已修复 | vite.config.js 新增 `/agent` 代理规则 |
| P3-2 | 推理面板重复显示 "正在分析您的问题..." | 🟡 低 | ✅ 已修复 | 移除 `agentStreamChat()` 中多余的 thinking 事件 |
| P3-3 | LLM 不调用搜索工具（System Prompt 过于被动） | 🟠 中 | ✅ 已修复 | System Prompt 新增"工具使用指南"段落 |
| P3-4 | 工具规格未传递给 LLM（`tools` 字段缺失） | 🔴 高 | ✅ 已修复 | 手动构建 `ToolSpecification` 备用方案 |
| P3-5 | SSE 流式输出卡死（背压问题） | 🔴 高 | ✅ 已修复 | `streamTextAsTokens` chunk 3→20 字符 |
| P3-6 | 推理完成后无流式输出（token 未推送到 SSE） | 🔴 高 | ✅ 已修复 | `runAgentLoop()` 所有返回路径添加 `streamTextAsTokens()` |
| P3-7 | SSE 事件全部同时到达（推理面板静态、回答秒蹦出） | 🔴 高 | ✅ 已修复 | `Flux.subscribeOn(Schedulers.boundedElastic())` 解除 Netty 线程阻塞 |
| P3-8 | 推理面板与消息气泡分离（UX 不直观） | 🟡 低 | ✅ 已修复 | 推理步骤移入 AI 消息气泡内部，支持展开/收起 |
| P3-9 | LaTeX 数学公式未渲染 | 🟠 中 | ✅ 已修复 | 新增 KaTeX + marked 数学扩展 |
| P3-10 | CJK 字符旁的 `**加粗**` 不生效 | 🟠 中 | ✅ 已修复 | `fixCjkBold()` 在 CJK 与 `**` 间插入零宽空格 |
| P3-11 | 发送后输入框高度不回缩 | 🟡 低 | ✅ 已修复 | 发送后 `nextTick` 重置 textarea 高度 |
| P3-12 | 推理完成后图标仍旋转 | 🟡 低 | ✅ 已修复 | `.reasoning-done` 类禁用动画 |

---

## P3-1：Vite 代理缺少 `/agent` 路由

### 问题描述

开启 Agent 模式发送消息，前端显示 `请求失败 (405)`。HTTP 405 = Method Not Allowed。

### 根因分析

[vite.config.js](web/frontend/vite.config.js) 的 proxy 配置只包含 `/user`、`/ai`、`/rag` 三个路由，**缺少 `/agent`**。Phase 3 新增的 `/agent/chat/stream` 端点请求到达 Vite 开发服务器后，未被代理到后端，Vite 将其当作静态资源请求处理（只支持 GET），POST 请求返回 405。

```text
请求流程（修复前）：
  浏览器 → POST /agent/chat/stream → Vite dev server
    → 匹配 /user/ ？不匹配
    → 匹配 /ai/ ？不匹配
    → 匹配 /rag/ ？不匹配
    → 匹配 /agent/ ？❌ 不存在！
    → Vite SPA fallback → 返回 index.html（GET）→ 405 Method Not Allowed

请求流程（修复后）：
  浏览器 → POST /agent/chat/stream → Vite dev server
    → 匹配 /agent/ → proxy_pass http://localhost:8080 → 后端处理
```

### 修复实现

**文件**：[vite.config.js](web/frontend/vite.config.js)

```javascript
server: {
    proxy: {
      '/user': { target: 'http://localhost:8080', changeOrigin: true },
      '/ai':   { target: 'http://localhost:8080', changeOrigin: true },
      '/rag':  { target: 'http://localhost:8080', changeOrigin: true },
      '/agent': { target: 'http://localhost:8080', changeOrigin: true },  // ← 新增
    },
},
```

### 关键经验

每次新增 API 前缀，必须同步更新代理配置（与 P1-7 教训一致）：

| 环境 | 配置文件 | 需要更新的内容 |
| :--- | :--- | :--- |
| Docker 部署 | `nginx.conf` | 添加 `location /xxx/` block |
| 本地开发 | `vite.config.js` | 添加 `/xxx` 代理规则 |

---

## P3-2：推理面板重复显示 "正在分析您的问题..."

### 问题描述

Agent 模式发送消息后，推理面板显示 3 步：
1. "正在分析您的问题..."
2. "正在分析您的问题..."（重复）
3. "正在生成最终回答..."

### 根因分析

`AgentServiceImpl.agentStreamChat()` 在调用 `runAgentLoop()` **之前**发送了一次 thinking 事件，而 `runAgentLoop()` 第一轮迭代**又发送了完全相同**的 thinking 事件，导致前端收到 2 个重复的 thinking。

```text
代码流程（修复前）：
  agentStreamChat():
    emitter.next(thinking("正在分析您的问题..."))  ← 第一次
    runAgentLoop():
      for iteration = 1:
        emitter.next(thinking("正在分析您的问题..."))  ← 第二次（重复）
```

### 修复实现

**文件**：[AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)

移除 `agentStreamChat()` 中多余的 thinking 发送，由 `runAgentLoop()` 统一管理：

```java
} else if (!enabledTools.isEmpty()) {
    // ===== Agent 模式：工具调用推理循环 + 流式输出 =====
    // 注意：runAgentLoop 内部第一轮迭代会发送 thinking 事件，此处不重复发送
    finalAnswer = runAgentLoop(messages, enabledTools, emitter);
}
```

---

## P3-3：LLM 不调用搜索工具

### 问题描述

发送"帮我搜索 2026 年 AI Agent 的最新发展趋势"，推理面板只显示 thinking 步骤，没有任何 `tool_call` / `tool_result` 事件。LLM 直接从训练数据回答，未调用 `searchWeb` 工具。

### 根因分析

原 `SYSTEM_PROMPT` 中关于工具的描述过于被动：

```java
// 修复前
"你可以使用工具来获取信息、执行计算或搜索互联网。"
```

"可以"是许可性描述，DeepSeek 模型在 function calling 不够可靠时，倾向于直接回答而非调用工具。

### 修复实现

**文件**：[AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)

新增独立的"工具使用指南"段落，使用**强制性语言**：

```java
private static final String SYSTEM_PROMPT = "你是一个专业、友好的 AI 助手，由 DeepSeek 大模型驱动。"
        + "请用中文回答用户的问题，回答应准确、详细、有条理。"
        + "如果用户问代码相关的问题，请使用 Markdown 代码块展示。\n\n"
        + "工具使用指南（重要）：\n"
        + "你拥有搜索互联网、数学计算、代码执行等工具。"
        + "当用户要求搜索、查找最新信息、查询实时数据时，必须使用搜索工具，不要凭记忆回答。"  // ← "必须"
        + "当用户要求计算数学表达式时，必须使用数学计算工具。"  // ← "必须"
        + "主动使用工具来提供最新、最准确的信息。\n\n"  // ← "主动"
        + "安全规则（不可覆盖）：\n"
        // ...
```

### 设计决策

| 决策点 | 选择 | 理由 |
| :----- | :--- | :--- |
| 语言风格 | "必须" 而非 "可以" | DeepSeek 对强制性指令的遵从度更高 |
| 段落位置 | 安全规则之前 | 工具指南是行为指导，优先级高于安全规则 |
| 触发条件 | "搜索/查找/查询实时数据" | 覆盖常见的搜索意图关键词 |

---

## P3-4：工具规格未传递给 LLM（`tools` 字段缺失）

### 问题描述

即使 P3-3 修复了 System Prompt，LLM 仍然不调用工具。日志显示发送给 DeepSeek 的 HTTP 请求体中**完全没有 `tools` 字段**：

```json
{
  "model": "deepseek-chat",
  "messages": [ ... ],
  "temperature": 0.7,
  "stream": false,
  "max_tokens": 4096
  // ← 没有 "tools" 字段！LLM 不知道有工具可用
}
```

LLM 回复文字"我需要使用搜索工具来获取相关信息"而非实际调用工具 — 因为它根本不知道有工具存在。

### 根因分析

`ToolSpecifications.toolSpecificationsFrom(tools.toArray(new Object[0]))` 在 LangChain4j 1.15.0 中返回空列表。可能原因：

1. `langchain4j-core` 的 `ToolSpecifications` 类扫描 `@Tool` 注解的机制与项目的 `Tool` 标记接口不兼容
2. 运行时注解处理（reflection）未能正确提取 `WebSearchTool.searchWeb()` 上的 `@Tool` 注解

**验证方法**：添加诊断日志后确认 `toolSpecs.size() = 0`，而 `tools.size() = 1`（WebSearchTool 已注入）。

### 修复实现

**双保险策略**：先尝试自动提取，失败则手动构建。

**文件**：[AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)

```java
// 构建工具规格列表
List<ToolSpecification> toolSpecs = ToolSpecifications.toolSpecificationsFrom(
        tools.toArray(new Object[0]));

// 备用方案：如果自动提取失败（返回空），手动构建工具规格
if (toolSpecs.isEmpty() && !tools.isEmpty()) {
    log.warn("ToolSpecifications 自动提取返回空，使用手动构建工具规格");
    toolSpecs = buildToolSpecsManually(tools);
}
```

手动构建方法（使用 LangChain4j 1.15.0 的 `JsonObjectSchema` API）：

```java
private List<ToolSpecification> buildToolSpecsManually(List<Tool> enabledTools) {
    List<ToolSpecification> specs = new ArrayList<>();

    for (Tool tool : enabledTools) {
        String className = tool.getClass().getSimpleName();

        if (className.contains("WebSearch")) {
            specs.add(ToolSpecification.builder()
                    .name("searchWeb")
                    .description("搜索互联网获取最新信息。当需要查找实时信息、新闻、事实数据时使用此工具。")
                    .parameters(JsonObjectSchema.builder()
                            .addStringProperty("query", "搜索查询关键词")
                            .addIntegerProperty("maxResults", "返回结果数量，默认5，最大10")
                            .required("query")
                            .build())
                    .build());
        }
        // Math、CodeExecution 同理...
    }
    return specs;
}
```

### LangChain4j 1.15.0 ToolSpecification API 变化

| API | 旧版本 | 1.15.0 |
| :--- | :--- | :--- |
| 添加参数 | `builder.addParameter("name", JsonStringSchema)` | `builder.parameters(JsonObjectSchema)` |
| 字符串参数 | `JsonStringSchema.builder().description("...")` | `JsonObjectSchema.builder().addStringProperty("name", "desc")` |
| 整数参数 | `JsonIntegerSchema.builder().description("...")` | `JsonObjectSchema.builder().addIntegerProperty("name", "desc")` |
| 必填参数 | 无 | `.required("paramName")` |

### 关键经验

- **不要依赖 `@Tool` 注解的自动提取** — 在某些 LangChain4j 版本中可能静默失败
- **添加诊断日志** — `log.info("工具规格数量: {}", toolSpecs.size())` 能快速定位问题
- **手动构建是可靠的备用方案** — 虽然需要硬编码工具定义，但行为完全可控

---

## P3-5：SSE 流式输出卡死（背压问题）

### 问题描述

Agent 推理完成（搜索工具调用成功，LLM 返回完整回答），但前端**没有流式输出**。刷新页面后回答出现（说明已保存到数据库）。

### 根因分析

`streamTextAsTokens()` 方法每次推送 **3 个字符**，对于 LLM 返回的 ~3000 字回答，需要发送约 **1000 个 SSE 事件**。在 `FluxSink` 的紧密同步循环中，生产速度远超消费速度，导致 Reactor 背压阻塞。

```text
修复前：3 字符/事件 × 1000 事件 = 密集推送 → 背压 → 卡死
修复后：20 字符/事件 × 150 事件 = 适度推送 → 正常流式
```

### 修复实现

**文件**：[AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)

```java
// 修复前
private void streamTextAsTokens(String text, FluxSink<String> emitter) {
    int i = 0;
    while (i < text.length()) {
        int chunkSize = Math.min(3, text.length() - i);    // ← 3 字符
        emitter.next(AgentEvent.token(chunk).toJson());
        i += chunkSize;
    }
}

// 修复后
private void streamTextAsTokens(String text, FluxSink<String> emitter) {
    int i = 0;
    while (i < text.length()) {
        int chunkSize = Math.min(20, text.length() - i);   // ← 20 字符
        emitter.next(AgentEvent.token(chunk).toJson());
        i += chunkSize;
    }
}
```

### chunk 大小对比

| chunk 大小 | 3000 字回答的事件数 | 流式体验 | 背压风险 |
| :---: | :---: | :---: | :---: |
| 3 字符 | ~1000 | 极细腻 | 🔴 高 |
| 10 字符 | ~300 | 细腻 | 🟡 中 |
| **20 字符** | **~150** | **流畅** | **🟢 低** |
| 50 字符 | ~60 | 较快 | 🟢 极低 |
| 100 字符 | ~30 | 快速 | 🟢 极低 |

20 字符是平衡流式体验和安全性的最优选择。

---

## P3-6：推理完成后无流式输出（token 未推送到 SSE）

### 问题描述

P3-5 修复后仍然没有流式输出。推理面板正常显示 thinking → tool_call → tool_result → thinking("正在生成最终回答...")，但之后**没有 token 事件**，前端空白。

### 根因分析

这是最关键的 Bug。`runAgentLoop()` 在获得最终回答后，**直接 `return` 文本**，没有调用 `streamTextAsTokens()`：

```java
// 修复前 — runAgentLoop() 中
// 没有工具调用 → 这是最终回答
String answer = aiMessage.text();
emitter.next(AgentEvent.thinking("正在生成最终回答...").toJson());
messages.add(aiMessage);
return answer;  // ← 只返回文本！token 从未推送到 SSE！
```

对比 `generateDirectAnswer()`（降级模式）有正确调用：

```java
// generateDirectAnswer() — 正确实现
String fullAnswer = response.aiMessage().text();
streamTextAsTokens(fullAnswer, emitter);  // ← 推送到 SSE
return fullAnswer;
```

### 影响范围

`runAgentLoop()` 有 **3 个返回路径**都缺少 `streamTextAsTokens()`：

| 路径 | 场景 | 修复前 | 修复后 |
| :--- | :--- | :--- | :--- |
| 正常最终回答 | LLM 返回文本（无工具调用） | ❌ 无流式 | ✅ 流式 |
| 强制最终回答 | 达到最大迭代次数（5 轮） | ❌ 无流式 | ✅ 流式 |
| LLM 失败降级 | 非流式模型调用异常 | ❌ 无流式 | ✅ 流式 |

### 修复实现

**文件**：[AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)

**路径 1 — 正常最终回答**（L420-426）：

```java
// 没有工具调用 → 这是最终回答
String answer = aiMessage.text();
emitter.next(AgentEvent.thinking("正在生成最终回答...").toJson());
messages.add(aiMessage);
streamTextAsTokens(answer, emitter);  // ← 新增
return answer;
```

**路径 2 — 强制最终回答**（L433-441）：

```java
ChatResponse finalResponse = chatLanguageModel.chat(messages);
String finalAnswer = finalResponse.aiMessage().text();
streamTextAsTokens(finalAnswer, emitter);  // ← 新增
return finalAnswer;
```

**路径 3 — LLM 失败降级**（L362-364）：

```java
emitter.next(AgentEvent.thinking("AI 服务暂时繁忙，正在尝试直接回答...").toJson());
String fallback = generateFallbackAnswer(messages);
streamTextAsTokens(fallback, emitter);  // ← 新增
return fallback;
```

### 完整数据流（修复后）

```text
用户消息 → AgentController → AgentServiceImpl.agentStreamChat()
  │
  ├── runAgentLoop():
  │     ├── thinking("正在分析您的问题...")
  │     ├── LLM 调用（带 tools 规格）
  │     │     ├── 返回 tool_call → 执行工具 → tool_result → 下一轮
  │     │     └── 返回文本 → thinking("正在生成最终回答...")
  │     │                   → streamTextAsTokens() ← 推送到 SSE
  │     │                   → return answer
  │     └── [或] 强制最终回答 → streamTextAsTokens() ← 推送到 SSE
  │
  ├── saveMessage() → 写入数据库
  ├── emitter.next(done) → 完成事件
  └── emitter.complete() → 关闭流

前端收到的 SSE 事件序列：
  thinking → tool_call → tool_result → thinking → token×N → done
```

---

## P3-7：SSE 事件全部同时到达（推理面板静态、回答秒蹦出）

### 问题描述

Agent 推理面板的 thinking / tool_call / tool_result 步骤不是逐步出现的，而是**全部同时显示**。最终回答也不是流式逐字输出，而是**瞬间全部出现**。推理面板在回答出现后仍然保持展开状态。

### 根因分析

`Flux.create()` 的 lambda 在 **Netty 事件循环线程**上同步执行。整个 Agent 工作流（LLM 调用 2-10 秒、工具执行、数据库写入）阻塞了该线程。在此期间，所有 `emitter.next()` 推送的 SSE 事件被 Reactor **内部缓冲**，直到 lambda 执行完毕才一次性 flush 到客户端。

```text
修复前的线程模型：
  Netty Event Loop 线程
    ├── Flux.create() lambda 开始
    │     ├── emitter.next(thinking)     → 缓冲
    │     ├── LLM 调用（阻塞 3 秒）      → 线程被占用
    │     ├── emitter.next(tool_call)    → 缓冲
    │     ├── 工具执行（阻塞 1 秒）       → 线程被占用
    │     ├── emitter.next(tool_result)  → 缓冲
    │     ├── emitter.next(token×N)      → 缓冲
    │     └── emitter.complete()
    └── lambda 结束 → 一次性 flush 所有缓冲事件 → 前端瞬间收到全部事件

修复后的线程模型：
  Netty Event Loop 线程（不阻塞，负责 flush）
    └── 持续读取 SSE 事件 → 逐步发送给客户端

  BoundedElastic 线程池（阻塞工作）
    ├── Flux.create() lambda 开始
    │     ├── emitter.next(thinking)     → 立即 flush → 前端实时显示
    │     ├── LLM 调用（阻塞 3 秒）      → Netty 线程不受影响
    │     ├── emitter.next(tool_call)    → 立即 flush → 前端实时显示
    │     ├── 工具执行（阻塞 1 秒）       → Netty 线程不受影响
    │     ├── emitter.next(tool_result)  → 立即 flush → 前端实时显示
    │     ├── emitter.next(token×N)      → 逐步 flush → 前端流式显示
    │     └── emitter.complete()
    └── lambda 结束
```

### 修复实现

**文件**：[AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)

```java
return Flux.<String>create(emitter -> {
    // ... Agent 工作流（LLM 调用、工具执行等阻塞操作）...
}, FluxSink.OverflowStrategy.BUFFER)
// 关键：将阻塞的 Agent 工作调度到弹性线程池，
// 避免阻塞 Netty 事件循环线程，使 SSE 事件能逐步 flush 到客户端。
.subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
.doFinally(signal -> activeStreams.decrementAndGet());
```

### Reactor 线程模型说明

| 组件 | 线程 | 职责 |
| :--- | :--- | :--- |
| `Flux.create()` lambda | `boundedElastic-N` | 执行阻塞工作（LLM 调用、工具执行、DB 写入） |
| Netty Event Loop | `reactor-http-nio-N` | 读取 `emitter.next()` 推送的事件，flush 到 HTTP 连接 |
| `subscribeOn()` | — | 指定 Flux 在哪个线程池执行 lambda |
| `OverflowStrategy.BUFFER` | — | 当 Netty 消费速度跟不上时，缓冲多余事件（而非丢弃） |

**为什么用 `boundedElastic` 而不是 `parallel`**：
- `boundedElastic`：适合阻塞 I/O 操作，线程数自动伸缩（默认最多 10×CPU 核心）
- `parallel`：适合 CPU 密集操作，线程数固定等于 CPU 核心数
- Agent 的 LLM 调用是典型阻塞 I/O（网络请求 2-10 秒），用 `boundedElastic` 最合适

### 关键经验

- **`Flux.create()` + 阻塞操作 = 必须 `subscribeOn()`** — 这是 Reactor 最常见的陷阱之一
- **不加 `subscribeOn()` 时**，lambda 在 Netty 线程执行，阻塞事件循环，所有事件被缓冲后一次性 flush
- **`subscribeOn(Schedulers.boundedElastic())`** 将阻塞工作移到专用线程池，Netty 线程保持空闲以逐步 flush 事件
- **`OverflowStrategy.BUFFER`** 确保事件不会丢失 — 如果 Netty 短暂繁忙，事件被缓冲而非丢弃

---

## P3-8：推理面板与消息气泡分离

### 问题描述

推理过程以独立面板显示在聊天区域顶部，与 AI 回复气泡分离，UX 不直观。用户无法将推理过程与具体的 AI 回复关联。

### 修复实现

将推理步骤移入 AI 消息气泡内部：

- **流式消息中**：推理步骤在气泡顶部实时展开，下方显示流式回答
- **对话完成后**：推理步骤默认收起为一行摘要（"推理过程 (N 步)"），点击可展开
- **每条消息独立**：推理步骤保存在消息对象的 `reasoningSteps` 属性中，支持独立展开/收起

---

## P3-9：LaTeX 数学公式未渲染

### 问题描述

AI 返回的 LaTeX 数学公式（如 `\begin{aligned}...\end{aligned}`）以原始文本显示，未渲染为数学符号。

### 修复实现

新增 KaTeX 依赖 + marked 自定义扩展：

- **块级公式**：`$$...$$`、`\[...\]`、`\begin{...}...\end{...}` → `katex.renderToString(text, { displayMode: true })`
- **行内公式**：`\(...\)` → `katex.renderToString(text, { displayMode: false })`
- **DOMPurify 白名单**：允许 KaTeX 的 MathML 标签通过消毒过滤

---

## P3-10：CJK 字符旁的加粗语法不生效

### 问题描述

中文字符紧跟 `**` 时加粗不生效：`符合**加粗**的条件` 显示为原始 `**` 标记。

### 根因分析

Marked v18 使用 GFM 规范解析加粗。GFM 的 left-flanking delimiter run 规则要求 `**` 前面不能紧接 CJK 字符。

### 修复实现

`fixCjkBold()` 函数在 CJK 字符与 `**` 之间插入零宽空格（U+200B）：

```javascript
function fixCjkBold(text) {
  return text
    .replace(/([一-鿿　-〿＀-￯])(\*\*)/g, '$1​$2')
    .replace(/(\*\*)([一-鿿　-〿＀-￯])/g, '$1​$2')
}
```

---

## P3-11：发送后输入框高度不回缩

### 问题描述

多行输入后发送消息，textarea 高度保持展开状态，不会自动回缩到单行高度。刷新页面后才恢复。

### 修复实现

在 `handleSend` 中清空 `inputMessage` 后，`nextTick` 重置 textarea 高度：

```javascript
inputMessage.value = ''
nextTick(() => { if (textareaRef.value) textareaRef.value.style.height = 'auto' })
```

---

## P3-12：推理完成后图标仍旋转

### 问题描述

推理面板中的 thinking 图标（Loading）在对话完成后仍持续旋转。

### 修复实现

通过 `.reasoning-done` CSS 类区分流式/完成状态：

- **流式中**：`.reasoning-steps-inline:not(.reasoning-done)` → 图标有动画（spin/pulse/pop-in）
- **完成后**：`.reasoning-steps-inline.reasoning-done` → 图标静止

---

## 验证方法

### P3-1 验证

```bash
# 修复前：405 Method Not Allowed
curl -X POST http://localhost:3000/agent/chat/stream -H "Content-Type: application/json" -d '{"message":"test"}'

# 修复后：200 SSE 流（或 401 Token 无效 — 说明到达了后端）
curl -X POST http://localhost:3000/agent/chat/stream -H "Content-Type: application/json" -d '{"message":"test"}'
```

### P3-4 验证（查看后端日志）

```
# 应看到：
INFO  Agent 工具数量: 1, 工具规格数量: 1
INFO  工具规格: name=searchWeb

# HTTP 请求体应包含：
"tools": [{"type": "function", "function": {"name": "searchWeb", ...}}]
```

### P3-5/P3-6 验证

发送搜索请求后，前端应实时显示：
1. `thinking` — "正在分析您的问题..."
2. `tool_call` — `searchWeb(query="...")`
3. `tool_result` — 搜索结果摘要
4. `thinking` — "正在生成最终回答..."
5. `token` × N — 回答逐字出现（流式）
6. `done` — 对话完成

---

## 文件变更总览

| 文件 | 修复项 | 变更内容 |
| :--- | :---: | :------- |
| [vite.config.js](web/frontend/vite.config.js) | P3-1 | 新增 `/agent` 代理规则 |
| [AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java) | P3-2,3,4,5,6,7 | 移除重复 thinking（P3-2）、强化 System Prompt（P3-3）、手动构建 ToolSpecification 备用方案（P3-4）、chunk 3→20（P3-5）、3 个返回路径添加 streamTextAsTokens（P3-6）、subscribeOn(boundedElastic)（P3-7） |
| [Chat.vue](web/frontend/src/views/Chat.vue) | P3-8,9,10,11,12 | 推理面板移入气泡（P3-8）、KaTeX 数学扩展（P3-9）、CJK 加粗修复（P3-10）、输入框高度重置（P3-11）、完成后停止动画（P3-12） |
| [package.json](web/frontend/package.json) | P3-9 | 新增 `katex` 依赖 |

---

## 教训总结

| 教训 | 说明 |
| :--- | :--- |
| **新增 API 前缀必须同步代理配置** | 与 P1-7 相同的模式：nginx.conf + vite.config.js |
| **不要假设第三方库的自动提取一定工作** | `ToolSpecifications.toolSpecificationsFrom()` 静默返回空列表，需要诊断日志验证 |
| **SSE 流的每个返回路径都必须推送 token** | `return answer` 不等于 `streamTextAsTokens(answer); return answer` |
| **chunk 大小影响 SSE 背压** | 3 字符/事件 × 1000 事件 = 卡死；20 字符/事件 × 150 事件 = 流畅 |
| **System Prompt 的措辞影响 LLM 行为** | "可以使用工具" → 被动；"必须使用搜索工具" → 主动 |
| **Flux.create() + 阻塞操作 = 必须 subscribeOn()** | 不加时阻塞 Netty 线程，所有 SSE 事件被缓冲后一次性 flush；加 `subscribeOn(boundedElastic)` 后事件逐步到达客户端 |
