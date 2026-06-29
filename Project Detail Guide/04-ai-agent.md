# Phase 3：AI Agent 智能体（已完成）

## 概述

Phase 3 在 AI 对话（Phase 1）和 RAG 知识库（Phase 2）基础上，新增 **AI Agent 智能体** 能力。Agent 与普通 AI 对话的核心区别是：Agent 可以**自主调用工具**（搜索网页、计算数学、执行代码）来完成复杂任务，而不是仅凭模型参数内的知识回答。

**核心架构**：两阶段流式 — 先用非流式模型完成"思考→工具调用→分析结果"循环，再用流式模型逐字输出最终回答。这样设计是因为 DeepSeek 不支持流式 function calling，而且工具调用过程本身不需要流式输出。

**与传统 Chat 的关系**：Agent 是独立于普通 Chat 的服务体系，不继承 `AiChatServiceImpl`。两者共享限流、注入检测、对话管理等基础设施模式，但 Agent 通过结构化 SSE 事件协议（`AgentEvent`）传输推理过程，前端可独立渲染思考步骤和工具调用卡片。

> **371 个单元测试**覆盖所有 Phase 3 模块（JUnit 5 + Mockito + MockMvc），零失败。


---

## Phase 3 构建全过程

### 第一步：新增 Agent 依赖 — langchain4j + exp4j

**操作**：在 [pom.xml](springboot/pom.xml) 中新增两个依赖：

```xml
<!-- ==================== AI Agent 扩展（Phase 3）==================== -->

<!-- langchain4j 核心模块：AiServices、ToolSpecification、@Tool 注解 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.15.0</version>
</dependency>

<!-- exp4j：安全的数学表达式求值引擎（MathTool 依赖） -->
<dependency>
    <groupId>net.objecthunter</groupId>
    <artifactId>exp4j</artifactId>
    <version>0.4.8</version>
</dependency>
```

**设计要点**：

- **为什么需要** `langchain4j` 核心模块？ Phase 1 只引入了 `langchain4j-open-ai`（OpenAI 兼容适配器），它传递依赖了 `langchain4j-core`（消息模型、ChatModel 接口），但 **不包含** `AiServices`（Agent 编排）、`@Tool` 注解、`ToolSpecification` 类。这些都在 `langchain4j` 核心模块中。Agent 的工具调用框架依赖这些类，必须显式引入。
- **为什么选 exp4j 而不是 JSR 223 ScriptEngine 做数学？** `ScriptEngine.eval("2+3")` 本质上是执行 JavaScript 代码——它和 CodeExecutionTool 共用同一个执行环境，存在注入风险。exp4j 是纯 Java 数学表达式解析器，只做数学运算，不执行代码。它的"语法树"只包含运算符、函数和数字，不存在代码注入可能。这也是为什么 MathTool 默认开启而 CodeExecutionTool 默认关闭。
- **exp4j 0.4.8 的功能范围**：支持 `+`, `-`, `*`, `/`, `^`（幂）, `%`（取模）, 自定义函数（sin/cos/tan/log/sqrt 等）, 自定义常量（pi/e）, 括号和运算符优先级。

**踩坑记录**：

1. `langchain4j-open-ai` 传递依赖的 `langchain4j-core` 版本可能与 `langchain4j` 主模块不一致 → 在 `<dependencyManagement>` 中统一版本或确保两者版本号相同
2. exp4j 的最新版是 0.4.8（发布较早但稳定），更高版本不存在于 Maven Central → 无需升级


---

### 第二步：AiConfig 改造 + AgentConfig — 双模型 Bean + 配置类

**操作**：修改 [AiConfig.java](springboot/src/main/java/com/zora/config/AiConfig.java)，新增 [AgentConfig.java](springboot/src/main/java/com/zora/config/AgentConfig.java)。

#### AiConfig 改造：新增非流式 ChatModel Bean

在原有 `StreamingChatModel` bean 基础上，新增一个同名配置的**非流式** bean：

```java
// ==================== Phase 3: 非流式 ChatModel（Agent 推理用）====================

/**
 * 非流式 ChatLanguageModel Bean
 * <p>
 * 用于 Agent 推理循环中的工具调用。
 * DeepSeek 不支持流式 function calling，因此推理阶段必须使用非流式模型。
 * 配置参数（baseUrl、apiKey、modelName）与流式模型共享同一组 application.yml 配置。
 * </p>
 */
@Bean
@ConditionalOnProperty(name = "deepseek.enabled", havingValue = "true", matchIfMissing = true)
public ChatLanguageModel chatLanguageModel() {
    return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .baseUrl(baseUrl)
            .modelName(modelName)
            .temperature(temperature)
            .maxTokens(maxTokens)
            .timeout(Duration.ofSeconds(120))  // 工具调用可能较长
            .build();
}
```

**关键**：两个 Bean 共享 `apiKey`, `baseUrl`, `modelName` 配置（从 `@Value` 注入），但 `StreamingChatModel` 用于最终回答的流式输出，`ChatLanguageModel`（非流式）用于推理循环中的工具调用决策。

**为什么需要两个模型 Bean？**：

- **流式模型**（`StreamingChatModel`）：通过回调函数 `onNext(token)` 逐 token 推送文本，适合最终回答的流式输出
- **非流式模型**（`ChatLanguageModel`）：返回完整 `ChatResponse`，其中包含 `AiMessage.hasToolExecutionRequests()` 可以检查 LLM 是否要调用工具。**LangChain4j 的** `StreamingChatModel` 不支持工具调用结果的同步处理——它通过回调异步推送，无法在推理循环中等待工具执行完毕后再决策

这就是"两阶段流式"架构的根源：不是因为偏好，而是 DeepSeek + LangChain4j 的技术限制。

#### AgentConfig：外部化 Agent 配置

新建 [AgentConfig.java](springboot/src/main/java/com/zora/config/AgentConfig.java)：

```java
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {
    /** 工具开关配置 */
    private ToolsConfig tools = new ToolsConfig();
    /** Tavily 搜索 API 配置 */
    private TavilyConfig tavily = new TavilyConfig();
    /** 多智能体编排配置（Phase 3.5） */
    private MultiAgentConfig multiAgent = new MultiAgentConfig();
    /** 记忆系统配置（Phase 3.4） */
    private MemoryConfig memory = new MemoryConfig();

    // ==================== 嵌套配置类 ====================

    @Data
    public static class ToolsConfig {
        private WebSearchConfig webSearch = new WebSearchConfig();
        private MathConfig math = new MathConfig();
        private CodeExecConfig codeExecution = new CodeExecConfig();
    }

    @Data
    public static class TavilyConfig {
        private String apiKey;
        private String baseUrl = "https://api.tavily.com/search";
        private int timeoutSeconds = 15;
    }

    @Data
    public static class WebSearchConfig {
        private boolean enabled = true;
    }

    @Data
    public static class MathConfig {
        private boolean enabled = true;
    }

    @Data
    public static class CodeExecConfig {
        private boolean enabled = false;      // 默认关闭（安全考量）
        private int timeoutSeconds = 5;
        private int maxOutputLength = 10000;
    }

    @Data
    public static class MultiAgentConfig {
        private boolean enabled = false;       // Phase 3.5
        private int maxSpecialistCalls = 3;
    }

    @Data
    public static class MemoryConfig {
        private int windowSize = 20;
        private int summaryTriggerCount = 10;
        private int summaryMaxLength = 300;
    }
}
```

**设计要点**：

- **嵌套配置类 +** `@ConfigurationProperties`：Spring Boot 自动将 `application.yml` 中的 `agent.tools.web-search.enabled` 映射到 `AgentConfig.ToolsConfig.WebSearchConfig.enabled`，无需手动 `@Value` 注入
- **默认值设计**：代码中设置默认值（`enabled = true`），`application.yml` 通过 `${ENV_VAR:default}` 覆盖。这是一个双重保障——即使配置文件缺失，系统也能用默认值正常运行
- `code-execution.enabled = false` 默认关闭：这是安全底线。JS 代码执行即使有沙箱也不是绝对安全的，必须让用户主动开启

#### application.yml 新增配置

```yaml
# ==================== Agent 智能体配置（Phase 3）====================
agent:
  tools:
    web-search:
      enabled: ${AGENT_TOOL_WEB_SEARCH:true}
    math:
      enabled: ${AGENT_TOOL_MATH:true}
    code-execution:
      enabled: ${AGENT_TOOL_CODE_EXEC:false}  # 默认关闭（安全考量）
      timeout-seconds: 5
      max-output-length: 10000
  tavily:
    api-key: ${TAVILY_API_KEY:}
    base-url: https://api.tavily.com/search
    timeout-seconds: 15
  multi-agent:
    enabled: ${AGENT_MULTI_AGENT:false}       # Phase 3.5
    max-specialist-calls: 3
  memory:
    window-size: 20
    summary-trigger-count: 10
    summary-max-length: 300
```

同步更新 `application-example.yml` 添加模板（用 `YOUR_TAVILY_API_KEY` 占位）。


---

### 第三步：结构化 SSE 事件 — AgentEvent

**操作**：创建 [AgentEvent.java](springboot/src/main/java/com/zora/agent/event/AgentEvent.java)。

Phase 1 的 SSE 协议是简单的 `{"type":"token","content":"..."}` JSON。对于 Agent 场景，需要区分"思考"、"工具调用"、"工具结果"、"回答片段"等不同事件——这就是 `AgentEvent` 类型系统的由来。

```java
public class AgentEvent {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String type;                // 事件类型
    private final Map<String, Object> data;   // 事件数据

    // ==================== 工厂方法 ====================

    /** 思考事件：Agent 正在分析问题、选择工具 */
    public static AgentEvent thinking(String content) { ... }

    /** 工具调用事件：Agent 决定调用某个工具 */
    public static AgentEvent toolCall(String tool, Map<String, Object> args) { ... }

    /** 工具结果事件：工具执行完毕 */
    public static AgentEvent toolResult(String tool, String content) { ... }

    /** 文本 token 事件：最终回答的文本片段（与原 SSE 协议兼容） */
    public static AgentEvent token(String content) { ... }

    /** 对话完成事件 */
    public static AgentEvent done(Long conversationId) { ... }

    /** 错误事件：错误信息（已脱敏） */
    public static AgentEvent error(String message) { ... }

    // ==================== 序列化 ====================

    /**
     * 序列化为 JSON，直接拼入 SSE "data:" 行
     * 输出格式: {"type":"thinking", "content":"正在分析..."}
     * 注意：data 字段被"扁平化"到顶层，方便前端解析
     */
    public String toJson() {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", type);
        envelope.putAll(data);  // 扁平化 data 字段到顶层
        return MAPPER.writeValueAsString(envelope);
    }
}
```

**设计要点**：

- **为什么不用标准的 JSON Schema 或 Protobuf？** Agent 事件本质是 SSE 流中的短消息，每条只有几十到几百字节。JSON 的灵活性（无需预定义 schema）和可读性（浏览器 Network 标签中直接可读）最适合调试阶段的快速迭代。生产环境若需更高的解析性能，可以后续加 MessagePack 编码。
- **为什么** `toJson()` 把 data 扁平化到顶层？ 前端 JavaScript 解析后直接 `event.type`、`event.content`，不需要 `event.data.content` 这种嵌套访问。这是作者从实际调试中总结的经验——多一层嵌套就多一行 `if (event.data)` 判空代码。
- `MAPPER` 是静态字段：`ObjectMapper` 是线程安全的，一个 `static final` 实例共享给所有 AgentEvent 使用，避免每次序列化都 new 一个。

**SSE 事件示例**：

```text
data: {"type":"thinking","content":"正在分析您的问题..."}
data: {"type":"tool_call","tool":"searchWeb","args":{"query":"AI 新闻 2025"}}
data: {"type":"tool_result","tool":"searchWeb","content":"[搜索到3条结果...]"}
data: {"type":"token","content":"根据搜索结果，2025年AI趋势包括..."}
data: {"type":"done","conversationId":42}
```


---

### 第四步：Agent 核心服务 — AgentService + AgentServiceImpl

**操作**：创建 [AgentService.java](springboot/src/main/java/com/zora/agent/AgentService.java)、[AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)、[Tool.java](springboot/src/main/java/com/zora/agent/tool/Tool.java)、[AgentStep.java](springboot/src/main/java/com/zora/entity/AgentStep.java)。

#### Tool 工具标记接口

```java
/**
 * 工具标记接口（Phase 3）
 * <p>
 * 所有 Agent 工具必须实现此接口。
 * Spring 通过 {@code @Autowired(required = false) List<Tool>} 自动发现所有 Tool Bean，
 * AgentServiceImpl 根据 {@link AgentConfig} 的开关配置过滤启用的工具。
 * </p>
 * <p>
 * 工具类使用 LangChain4j 的 {@code @dev.langchain4j.agent.tool.Tool} 注解声明方法——
 * LLM 根据注解中的描述文字决定何时调用哪个工具。
 * </p>
 */
public interface Tool {
}
```

为什么需要这个空接口？因为 Spring 注入 `List<SomeInterface>` 必须有一个接口类型——`@Component` 注解本身无法让 Spring 按类型收集 Bean。`Tool` 就是这个"收集标记"。

#### AgentService 接口

```java
public interface AgentService {
    /**
     * Agent 流式对话
     * @param email 当前用户邮箱
     * @param message 用户消息
     * @param conversationId 对话 ID（新建为 null）
     * @return SSE 事件流 (Flux<String>)
     */
    Flux<String> agentStreamChat(String email, String message, Long conversationId);
}
```

#### AgentServiceImpl 核心实现

```java
@Service
public class AgentServiceImpl implements AgentService {

    @Resource private UserMapper userMapper;
    @Resource private ChatConversationMapper conversationMapper;
    @Resource private ChatMessageMapper messageMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private AgentConfig agentConfig;
    @Resource private StreamingChatModel streamingModel;  // 流式：最终回答输出
    @Resource private ChatLanguageModel chatModel;         // 非流式：推理循环

    // Spring 自动收集所有 Tool Bean
    @Autowired(required = false)
    private List<Tool> allTools;

    // Agent 循环最大次数（防止无限调用）
    private static final int MAX_AGENT_ITERATIONS = 5;
    // 限流：每分钟每用户最多 10 次 Agent 请求
    private static final int AGENT_RATE_LIMIT = 10;

    @Override
    public Flux<String> agentStreamChat(String email, String message, Long conversationId) {
        return Flux.create(emitter -> {
            try {
                // 1. 速率限制（Redis ZSET 滑动窗口，key: agent_rate:{email}）
                checkRateLimit(email);

                // 2. 注入检测（18 种中英文 Prompt Injection 模式）
                checkInjection(message);

                // 3. 加载/创建对话和用户
                User user = userMapper.selectByEmail(email);
                ChatConversation conv = getOrCreateConversation(user.getId(), conversationId);

                // 4. 获取已启用的工具
                List<Tool> enabledTools = getEnabledTools();
                List<ToolSpecification> toolSpecs = enabledTools.isEmpty() ? null
                    : ToolSpecifications.toolSpecificationsFrom(enabledTools);

                // 5. 构建消息历史
                List<ChatMessage> history = buildHistory(conv.getId(), message);

                // ========== 推理阶段：非流式循环 ==========
                for (int i = 0; i < MAX_AGENT_ITERATIONS && !emitter.isCancelled(); i++) {
                    emitter.next(AgentEvent.thinking(
                        i == 0 ? "正在分析您的问题..." : "正在分析工具返回的结果...").toJson());

                    ChatRequest request = ChatRequest.builder()
                        .messages(history)
                        .toolSpecifications(toolSpecs)
                        .build();

                    ChatResponse response = chatModel.chat(request);
                    AiMessage aiMessage = response.aiMessage();

                    // LLM 想调用工具 → 执行工具 → 将结果加入消息历史 → 继续循环
                    if (aiMessage.hasToolExecutionRequests()) {
                        for (ToolExecutionRequest toolReq : aiMessage.getToolExecutionRequests()) {
                            emitter.next(AgentEvent.toolCall(toolReq.name(), toolReq.arguments()).toJson());

                            String result = executeToolByName(toolReq.name(), toolReq.arguments());
                            emitter.next(AgentEvent.toolResult(toolReq.name(), result).toJson());

                            history.add(new ToolExecutionResultMessage(toolReq.id(), toolReq.name(), result));
                        }
                        continue;  // 继续循环，让 LLM 分析工具结果
                    }

                    // LLM 生成最终回答 → 转流式输出
                    history.add(aiMessage);
                    break;
                }

                // ========== 输出阶段：流式输出最终回答 ==========
                StringBuilder fullAnswer = new StringBuilder();
                streamingModel.chat(history, new StreamingChatResponseHandler() {
                    @Override public void onNext(String token) {
                        emitter.next(AgentEvent.token(token).toJson());
                        fullAnswer.append(token);
                    }
                    @Override public void onComplete(ChatResponse response) {
                        // 保存对话到 MySQL
                        saveMessages(conv, message, fullAnswer.toString());
                        emitter.next(AgentEvent.done(conv.getId()).toJson());
                        emitter.complete();
                    }
                    @Override public void onError(Throwable error) {
                        emitter.next(AgentEvent.error("AI 服务异常").toJson());
                        emitter.complete();
                    }
                });

            } catch (Exception e) {
                emitter.next(AgentEvent.error(e.getMessage()).toJson());
                emitter.complete();
            }
        });
    }

    /**
     * 获取已启用的工具列表
     * <p>
     * 三层过滤：① Spring 自动注入所有 @Component Tool Bean → 
     * ② AgentConfig.enabled 开关过滤 → ③ 按类名匹配具体配置
     * </p>
     */
    private List<Tool> getEnabledTools() {
        if (allTools == null) return Collections.emptyList();
        return allTools.stream().filter(tool -> {
            String className = tool.getClass().getSimpleName();
            if (className.contains("WebSearch")) return agentConfig.getTools().getWebSearch().isEnabled();
            if (className.contains("Math")) return agentConfig.getTools().getMath().isEnabled();
            if (className.contains("CodeExecution")) return agentConfig.getTools().getCodeExecution().isEnabled();
            return true;  // 未知工具默认启用
        }).collect(Collectors.toList());
    }
}
```

**设计要点**：

- **为什么不用 LangChain4j 的** `AiServices`？ `AiServices` 是一个声明式 API——你定义接口 + `@SystemPrompt` 注解，LangChain4j 自动生成代理对象处理工具调用。但它的内部工具循环是**黑盒的**——你无法拦截中间事件（thinking 思考了多久、调用了哪个工具、工具返回了什么）。我们自定义循环的目的是**向 SSE 流推送这些中间事件**，所以无法用 `AiServices`。
- **为什么推理阶段不用流式？** DeepSeek 的流式 API 返回的 token 是逐个推送的，没有"等待所有 token 再判断"的机制。如果 LLM 在流式返回中途触发 function calling token，客户端需要中断当前流、处理工具、再发起新请求——这比非流式复杂得多。LangChain4j 1.15.0 的 `StreamingChatModel` 对 function calling 的支持有限，我们选择在推理阶段用非流式，只在最后回答阶段用流式。
- `MAX_AGENT_ITERATIONS = 5`：防止 LLM 陷入"调用工具→不满意→再调用→再不满意"的死循环。每次循环消耗一次 LLM API 调用（付费的），5 次上限既保证了足够解决复杂问题，也防止了成本失控。
- **注入检测模式复用**：Agent 复用 Phase 1 中 `AiChatServiceImpl` 的 18 种中英文 Prompt Injection 检测模式。两种服务共享相同的正则表达式模式库。

**踩坑记录**：

1. `ToolSpecifications.toolSpecificationsFrom()` 的参数是 `Object`（工具实例），而非 `Class<?>`——它通过反射读取每个方法的 `@Tool` 注解来生成 schema。如果传入的类没有 `@Tool` 注解，这个方法返回空列表，不会有任何错误提示
2. `ChatLanguageModel.chat()` 不是 `generate()`——LangChain4j 1.15.0 中 `ChatModel` 接口的方法名是 `chat(ChatRequest)`，返回 `ChatResponse`。旧版 `generate()` 已废弃
3. `ToolExecutionRequest.arguments()` 返回的是 JSON 字符串，不是 `Map`——需要用 `ObjectMapper.readValue()` 解析。调用工具时需要把字符串转为 Map 传给 `@P` 注解的参数


---

### 第五步：Agent Controller — /agent/chat/stream + WebConfig 更新

**操作**：创建 [AgentController.java](springboot/src/main/java/com/zora/controller/AgentController.java)，修改 [WebConfig.java](springboot/src/main/java/com/zora/config/WebConfig.java)。

#### AgentController

```java
@RestController
@RequestMapping("/agent")
@Tag(name = "AI Agent 智能体", description = "基于 LangChain4j Tool Calling 的 AI Agent 系统")
public class AgentController {

    private static final int MAX_MESSAGE_LENGTH = 4000;

    @Resource
    private AgentService agentService;

    @Operation(summary = "Agent 流式对话",
        description = "发送消息给 AI Agent，Agent 可自主调用工具。返回 SSE 流。")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentStreamChat(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String email = (String) request.getAttribute("userEmail");
        String message = (String) body.get("message");
        Long conversationId = body.get("conversationId") != null
                ? Long.valueOf(body.get("conversationId").toString())
                : null;

        if (message == null || message.isBlank()) {
            return Flux.error(new IllegalArgumentException("消息不能为空"));
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return Flux.error(new IllegalArgumentException(
                    "消息长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符"));
        }

        return agentService.agentStreamChat(email, message, conversationId);
    }
}
```

**设计要点**：

- **端点路径** `/agent/chat/stream` 独立于 `/ai/chat/stream`：两者不共享请求体和响应协议。`/ai/*` 用 `{"type":"token","content":"..."}` 协议，`/agent/*` 用 `AgentEvent` 结构化协议。前端通过 `agentMode` 开关选择调用哪个端点。
- `@Tag` + `@Operation` 注解：确保 Knife4j 能自动扫描并生成 API 文档，Agent 端点会出现在 `http://localhost:8080/doc.html` 的分组中

#### WebConfig 更新

`/agent/**` 路径需要通过 `LoginInterceptor` 认证（与 `/ai/**` 一致）。在 `WebConfig.addInterceptors()` 中 `/agent/**` 路径已自动被拦截（只有显式添加到 `excludePathPatterns` 的路径才跳过拦截），**不需要额外修改**。因为 `LoginInterceptor` 拦截所有非排除路径，`/agent/**` 不在排除列表中，自动受保护。


---

### 第六步：WebSearchTool — Tavily Search API 网页搜索

**操作**：创建 [WebSearchTool.java](springboot/src/main/java/com/zora/agent/tool/WebSearchTool.java)。

```java
@Component
public class WebSearchTool implements Tool {

    @Resource
    private AgentConfig agentConfig;

    /**
     * 搜索互联网获取最新信息
     * <p>
     * 调用 Tavily Search API 执行网页搜索，返回 JSON 格式的结构化结果。
     * 当用户询问实时信息、新闻、事实数据时，AI 会自动调用此工具。
     * </p>
     *
     * @param query 搜索查询关键词
     * @param maxResults 返回结果数量（默认5，最大10）
     * @return JSON 搜索结果
     */
    @dev.langchain4j.agent.tool.Tool("搜索互联网获取最新信息。当需要查找实时信息、新闻、事实数据、最新进展时使用此工具")
    public String searchWeb(
            @P("搜索查询关键词，使用简洁的关键词组合") String query,
            @P("返回结果数量，默认5，最大10") Integer maxResults) {

        if (agentConfig.getTavily().getApiKey() == null
                || agentConfig.getTavily().getApiKey().isEmpty()) {
            return "{\"error\": \"搜索服务未配置 API Key\"}";
        }

        int limit = maxResults != null ? Math.min(maxResults, 10) : 5;

        // 构建请求体
        String requestBody = String.format(
            "{\"api_key\":\"%s\",\"query\":\"%s\",\"max_results\":%d}",
            agentConfig.getTavily().getApiKey(), query, limit);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(agentConfig.getTavily().getBaseUrl()))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(agentConfig.getTavily().getTimeoutSeconds()))
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        try {
            HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return response.body();  // Tavily 返回的就是 JSON
            }
            return String.format("{\"error\":\"搜索请求失败: HTTP %d\"}",
                response.statusCode());
        } catch (Exception e) {
            return String.format("{\"error\":\"搜索异常: %s\"}", e.getMessage());
        }
    }
}
```

**设计要点**：

- **为什么用** `java.net.http.HttpClient` 而不是 RestTemplate？ `HttpClient` 是 JDK 11+ 内置的 HTTP 客户端，无需额外依赖。Agent 工具只需要简单的 POST 请求，不需要 RestTemplate 的连接池、负载均衡等企业级特性
- `@Tool` 注解中的描述文字是给 LLM 看的：LLM 根据这段描述决定"这个工具能做什么，什么时候用"。描述应该简洁、准确、覆盖典型使用场景。坏的描述："搜索功能"；好的描述："搜索互联网获取最新信息。当需要查找实时信息、新闻、事实数据、最新进展时使用此工具"
- **Tavily API 免费额度**：1000 次/月，结构化 JSON 响应（title + url + content），比传统 Google Search API 更适合 AI Agent 消费
- **结果不加工直接透传**：工具返回的 JSON 直接交给 LLM 理解——人工不需要读懂中间结果。这减少了工具层的格式化逻辑


---

### 第七步：MathTool — exp4j 安全数学表达式求值

**操作**：创建 [MathTool.java](springboot/src/main/java/com/zora/agent/tool/MathTool.java)。

```java
@Component
public class MathTool implements Tool {

    /**
     * 执行数学计算
     * <p>
     * 使用 exp4j 安全解析数学表达式，支持 16 种函数 + 阶乘运算符。
     * 与 JavaScript eval() 不同，exp4j 只解析数学表达式，无法执行任意代码。
     * </p>
     *
     * @param expression 数学表达式
     * @return JSON: {"expression":"...","result":...} 或 {"error":"..."}
     */
    @dev.langchain4j.agent.tool.Tool("执行数学计算。支持基本运算(+,-,*,/,^,%)、三角函数(sin,cos,tan)、" +
            "对数(log,log2,log10,ln)、平方根(sqrt)、立方根(cbrt)、绝对值(abs)、" +
            "取整(floor,ceil,round)、常数(pi,e)、阶乘(5!)")
    public String calculate(
            @P("数学表达式，例如 '2+3*4'、'sqrt(144)'、'sin(pi/2)'、'5!'") String expression) {

        if (expression == null || expression.trim().isEmpty()) {
            return "{\"error\":\"表达式不能为空\"}";
        }

        try {
            // 构建自定义函数和操作符
            Expression exp = new ExpressionBuilder(expression)
                .functions(buildCustomFunctions())
                .operator(new FactorialOperator())  // 自定义阶乘运算符 !
                .variables("pi", "e", "π")
                .build()
                .setVariable("pi", Math.PI)
                .setVariable("e", Math.E)
                .setVariable("π", Math.PI);

            double result = exp.evaluate();
            return String.format("{\"expression\":\"%s\",\"result\":%s}",
                escapeJson(expression), formatResult(result));
        } catch (Exception e) {
            return String.format("{\"error\":\"%s\"}", escapeJson(e.getMessage()));
        }
    }

    /** 自定义函数集：sin, cos, tan, asin, acos, atan, sinh, cosh, tanh, log2, log10, ln, cbrt, round, floor, ceil */
    private Function[] buildCustomFunctions() { ... }

    /** 阶乘运算符：5! = 120 */
    private static class FactorialOperator extends Operator {
        public FactorialOperator() {
            super("!", 1, true, PRECEDENCE_POWER + 1);
        }
        @Override public double apply(double... args) {
            int n = (int) args[0];
            if (n < 0) throw new ArithmeticException("阶乘不支持负数");
            double result = 1;
            for (int i = 2; i <= n; i++) result *= i;
            return result;
        }
    }
}
```

**设计要点**：

- **exp4j 的安全性**：与 JavaScript `eval()` 不同，exp4j 构建的是一个**数学语法树**——解析器只接受运算符、函数和数字，任何非数学的 token（如 `System.exit(0)`）都会被拒绝。不存在代码注入的风险
- **16 个自定义函数**：exp4j 内置的只有 abs、sqrt、log 少数几个。sin/cos/tan 等三角函数和 log2/log10/ln 等对数函数都需要手动注册。`Functions` 类提供便捷的工厂方法创建单参数函数
- **阶乘是"运算符"而非"函数"**：`5!` 是后缀语法（操作数在运算符前面），而函数是前缀语法（`sqrt(144)`）。exp4j 的 `Operator` 类支持后缀操作符
- `escapeJson()` 处理特殊字符：用户输入的表达式可能包含 `"` 或 `\`，直接用 `String.format()` 拼 JSON 会破坏 JSON 结构。`escapeJson()` 方法转义这些字符


---

### 第八步：CodeExecutionTool — JS ScriptEngine 沙箱执行

**操作**：创建 [CodeExecutionTool.java](springboot/src/main/java/com/zora/agent/tool/CodeExecutionTool.java)。

```java
@Component
public class CodeExecutionTool implements Tool {

    @Resource
    private AgentConfig agentConfig;

    /**
     * 在沙箱中执行 JavaScript 代码片段
     * <p>
     * 安全措施：5 秒超时、输出截断（10000 字符）、审计日志。
     * 默认关闭，需通过 agent.tools.code-execution.enabled=true 开启。
     * </p>
     *
     * @param code JavaScript 代码
     * @param language 编程语言（目前仅支持 "javascript"）
     * @return JSON 执行结果
     */
    @dev.langchain4j.agent.tool.Tool("执行 JavaScript 代码片段。代码在沙箱中执行，超时5秒。适用于小型计算、数据处理")
    public String executeCode(
            @P("JavaScript 代码内容") String code,
            @P("编程语言，目前仅支持javascript") String language) {

        // 参数校验
        if (code == null || code.trim().isEmpty()) {
            return "{\"error\":\"代码不能为空\"}";
        }
        if (!"javascript".equalsIgnoreCase(language)) {
            return "{\"error\":\"仅支持 JavaScript 语言\"}";
        }

        // 获取 JS 引擎（兼容 JDK 15+ Nashorn 已移除的情况）
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("javascript");
        if (engine == null) engine = manager.getEngineByName("graal.js");
        if (engine == null) engine = manager.getEngineByName("nashorn");
        if (engine == null) {
            return "{\"error\":\"JavaScript 执行环境不可用（JDK 15+ 需安装 GraalVM JS）\"}";
        }

        // 审计日志
        log.info("CodeExecution: lang=js, codeLength={}, timeout={}s",
            code.length(), agentConfig.getTools().getCodeExecution().getTimeoutSeconds());

        // 捕获 stdout/stderr
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        System.setOut(new PrintStream(outStream));
        System.setErr(new PrintStream(outStream));

        final ScriptEngine jsEngine = engine;
        try {
            // ExecutorService + Future.get(timeout) 实现超时
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Object> future = executor.submit(() -> jsEngine.eval(code));

            int timeout = agentConfig.getTools().getCodeExecution().getTimeoutSeconds();
            Object rawResult = future.get(timeout, TimeUnit.SECONDS);

            String stdoutRaw = outStream.toString();
            int maxLen = agentConfig.getTools().getCodeExecution().getMaxOutputLength();
            String stdout = stdoutRaw.length() > maxLen
                ? stdoutRaw.substring(0, maxLen) + "\n...(输出已截断)"
                : stdoutRaw;

            return String.format(
                "{\"success\":true,\"result\":\"%s\",\"stdout\":\"%s\"}",
                rawResult != null ? rawResult.toString() : "undefined",
                escapeJson(stdout));

        } catch (TimeoutException e) {
            return "{\"error\":\"代码执行超时}";
        } catch (Exception e) {
            return String.format("{\"error\":\"%s\"}", escapeJson(e.getMessage()));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }
}
```

**设计要点**：

- **为什么默认关闭？** 即使有超时和输出限制，`ScriptEngine` 仍不是完全安全的沙箱——JDK 15+ 移除了 Nashorn，而 GraalVM JS 在沙箱隔离方面也有已知的绕过方法。默认关闭是安全底线
- `stdout`/`stderr` 捕获：`System.setOut/Err()` 重定向到 `ByteArrayOutputStream`，让 `console.log()` 和 `print()` 的输出被捕获作为工具返回结果的一部分——这对 LLM 理解代码执行结果很有用
- `final ScriptEngine jsEngine = engine`：匿名内部类和 lambda 表达式只能引用 effectively-final 的局部变量。因为 `engine` 被赋值了 3 次（依次尝试不同引擎名），需要引入 `jsEngine` 这个 final 引用才能在 lambda 中使用
- **超时用** `ExecutorService + Future.get(timeout)`：比 `Thread.stop()` 优雅——线程会被 interrupt，但不会造成资源泄露

**踩坑记录**：

1. JDK 15+ Nashorn 已移除 → `ScriptEngineManager().getEngineByName("nashorn")` 返回 null。代码按 "javascript" → "graal.js" → "nashorn" 顺序尝试，任一可用即可
2. Lambda 中引用非 final 变量编译错误 → 引入 `final ScriptEngine jsEngine = engine;` 行解决问题
3. `stdout`/`stderr` 变量被 `if-else` 重新赋值导致 effectively final 错误 → 改用 `stdoutRaw` + `stdout` 两次赋值的模式


---

### 第九步：前端 Agent SSE 客户端 — api/agent.js

**操作**：创建 [agent.js](web/frontend/src/api/agent.js)。

Agent 的 SSE 协议与普通对话不同——不是简单的 `{"type":"token"}` JSON，而是 6 种结构化事件。前端需要一个专门的解析器。

```js
/**
 * Agent SSE 流式对话
 * <p>
 * 与普通对话 ({@link streamChat}) 的区别：
 * - 端点：/agent/chat/stream vs /ai/chat/stream
 * - 事件：结构化事件（thinking/tool_call/tool_result/token/done/error）
 * - 回调：6 个独立回调函数，非单一 onToken
 * </p>
 *
 * @param {string} message - 用户消息
 * @param {number|null} conversationId - 会话 ID
 * @param {function} onThinking - (content) => void
 * @param {function} onToolCall - (tool, args) => void
 * @param {function} onToolResult - (tool, content) => void
 * @param {function} onToken - (content) => void
 * @param {function} onDone - (conversationId) => void
 * @param {function} onError - (error) => void
 * @returns {AbortController}
 */
export function streamAgentChat(
    message, conversationId,
    onThinking, onToolCall, onToolResult,
    onToken, onDone, onError
) {
    const token = getToken()
    const controller = new AbortController()

    fetch('/agent/chat/stream', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': token || '',
        },
        body: JSON.stringify({ message, conversationId: conversationId || null }),
        signal: controller.signal,
    })
    .then(async (response) => {
        if (response.status === 401) { onError(new Error('Token 已过期')); return }
        if (!response.ok) {
            const err = await response.json().catch(() => ({}))
            onError(new Error(err.msg || `请求失败 (${response.status})`))
            return
        }

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
                    const raw = line.slice(5).trim()
                    if (!raw) continue
                    try {
                        const event = JSON.parse(raw)
                        dispatchEvent(event, onThinking, onToolCall, onToolResult, onToken, onDone, onError)
                    } catch {
                        onToken(raw)  // 兼容非 JSON 格式
                    }
                }
            }
        }
        onDone(null)
    })
    .catch(err => { if (err.name !== 'AbortError') onError(err) })

    return controller
}

/** 分发 SSE 事件到对应回调 */
function dispatchEvent(event, onThinking, onToolCall, onToolResult, onToken, onDone, onError) {
    switch (event.type) {
        case 'thinking':    onThinking?.(event.content || ''); break
        case 'tool_call':   onToolCall?.(event.tool || 'unknown', event.args || {}); break
        case 'tool_result': onToolResult?.(event.tool || 'unknown', event.content || ''); break
        case 'token':       onToken?.(event.content || ''); break
        case 'done':        onDone?.(event.conversationId || null); break
        case 'error':       onError?.(event.message || '未知错误'); break
        default:            if (event.content) onToken?.(event.content)
    }
}
```

**设计要点**：

- **为什么暴露 6 个回调而非 1 个** `onEvent` 回调？ 6 个独立回调让调用方（Chat.vue）可以直接注册对应逻辑，不需要在 `onEvent` 内部写 `if-else` 判断事件类型。这是一种"将 switch-case 从业务组件层下沉到 API 层"的设计——`dispatchEvent()` 处理了事件路由，Chat.vue 只需关心"收到思考内容时做什么"
- `buffer` 处理 SSE 分片：SSE 流可能在任意位置分片传输（比如一个 "data:" 行被切成两个 TCP 包），buffer 模式保留最后一行不完整的部分，等待下一个 chunk 补全
- `AbortController` 返回：允许 Chat.vue 通过 `abortController.abort()` 取消正在进行的 SSE 流——用户点击"停止"按钮时触发


---

### 第十步：前端 Agent 可视化 — Chat.vue 推理面板

**操作**：修改 [Chat.vue](web/frontend/src/views/Chat.vue)，新增 ~120 行代码。

Chat.vue 的改动分为四个层次：模板（UI）、脚本（状态和逻辑）、样式（CSS）、导入。

#### 模板：新增三个 UI 区域

**（1）Agent 模式开关** — 在 chat-header 中，与 RAG 开关并列：

```html
<!-- Phase 3.3: Agent 智能体开关 -->
<el-switch
  v-model="agentMode"
  size="small"
  active-text="Agent"
  style="margin-right: 8px"
/>
```

**（2）推理面板** — chat-header 下方，可折叠：

```html
<Transition name="reasoning-panel">
  <div v-if="agentMode && reasoningSteps.length > 0 && showReasoning" class="reasoning-panel">
    <div class="reasoning-header">
      <div class="reasoning-title">
        <el-icon><Cpu /></el-icon>
        <span>推理过程</span>
        <span class="reasoning-count">{{ reasoningSteps.length }} 步</span>
      </div>
      <button @click="showReasoning = false" title="收起">
        <el-icon><ArrowUp /></el-icon>
      </button>
    </div>
    <div class="reasoning-steps">
      <div v-for="(step, idx) in reasoningSteps" :key="idx"
           class="reasoning-step" :class="'step-' + step.type">
        <!-- thinking: 蓝色 Loading 图标 + 思考文本 -->
        <!-- tool_call: 琥珀色工具图标 + 工具名 + 可展开参数 -->
        <!-- tool_result: 绿色对勾 + 结果摘要 -->
      </div>
    </div>
  </div>
</Transition>
```

**（3）思考指示器** — 流式输出区域，Agent 推理中尚未输出 token 时：

```html
<span v-if="agentMode && !streamingContent" class="agent-thinking-label">
  <el-icon class="spin-icon"><Cpu /></el-icon>
  思考中...
</span>
```

#### 脚本：三模式分发

核心逻辑在 `handleSend()` 函数中——根据 `agentMode` 和 `ragEnabled` 选择调用方式：

```js
// 新增状态变量
const agentMode = ref(false)           // Agent 模式开关
const reasoningSteps = ref([])         // 推理步骤 [{type, content/tool/args, ts}]
const showReasoning = ref(true)        // 推理面板展开/收起

// handleSend 中的三模式分发
if (agentMode.value) {
    // Agent 模式：streamAgentChat
    abortController = streamAgentChat(msg, convId,
        (content) => { reasoningSteps.value.push({ type: 'thinking', content, ts: Date.now() }) },
        (tool, args) => { reasoningSteps.value.push({ type: 'tool_call', tool, args, ts: Date.now() }) },
        (tool, content) => { reasoningSteps.value.push({ type: 'tool_result', tool, content, ts: Date.now() }) },
        (token) => { streamingContent.value += token },
        async (conversationId) => { /* 保存消息到 messages[] */ },
        (error) => { ElMessage.error(error.message) }
    )
} else {
    // RAG / 标准模式——原有逻辑
}

// 辅助函数：格式化工具参数为 key=value 字符串
const formatToolArgs = (args) => { /* Object.entries → key=value, 截断60字符 */ }

// 辅助函数：格式化工具结果为摘要
const formatToolResult = (content) => { /* JSON解析 → 提取result/answer/results等字段, 截断120字符 */ }
```

**设计要点**：

- `reasoningSteps` 独立于 `messages`：推理步骤（thinking/tool_call/tool_result）是 Agent 的"内部元数据"，不应混入对话历史。刷新页面后，推理步骤不会从数据库恢复——它们只存在于当前会话的浏览器内存中。这与 ChatGPT、Kimi 等产品的行为一致
- **色彩编码设计**：蓝色（思考）= 信任和安全、琥珀色（工具调用）= 注意和等待、绿色（工具结果）= 成功和完成。色彩的一致性降低了用户的认知负担——看到琥珀色就知道"AI 正在找外部信息"
- `formatToolResult()` 的智能解析：不同工具返回不同格式的 JSON——MathTool 返回 `{"expression":"...","result":...}`，WebSearchTool 返回 `{"results":[...]}`，CodeExecutionTool 返回 `{"success":true,...}`。这个函数通过 try-catch + 字段探测统一格式化为一行摘要
- **Vue** `<Transition>` + `max-height` 技巧：纯 CSS transition 不支持 `height: auto`（因为浏览器需要计算具体值）。设置 `max-height` 为一个大过实际内容的值（300px），从 `max-height: 0` 过渡到 `max-height: 300px`，实现了"展开/收起"的平滑动画

#### 样式：色彩编码 + 动画

```css
/* 推理面板 — 淡蓝背景 + 蓝色边框 */
.reasoning-panel { background: #fafbff; border: 1px solid #d6e4ff; border-radius: 10px; }

/* 步骤色彩编码 — 左边框 + 微妙背景色 */
.step-thinking  { border-left-color: #1677ff; background: #f0f5ff; }  /* 蓝色 = 思考 */
.step-tool_call { border-left-color: #f59e0b; background: #fffbeb; }  /* 琥珀 = 工具调用 */
.step-tool_result { border-left-color: #10b981; background: #f0fdf6; } /* 绿色 = 工具结果 */

/* 新步骤上滑淡入动画 */
@keyframes step-enter {
    from { opacity: 0; transform: translateY(-6px); }
    to   { opacity: 1; transform: translateY(0); }
}
.reasoning-step { animation: step-enter 0.25s ease-out; }

/* 旋转 CPU 图标（思考指示器） */
@keyframes spin {
    from { transform: rotate(0deg); }
    to   { transform: rotate(360deg); }
}
.spin-icon { animation: spin 1.2s linear infinite; }

/* 面板过渡动画 — max-height 从 0 到 300px */
.reasoning-panel-enter-active, .reasoning-panel-leave-active { transition: all 0.25s ease; }
.reasoning-panel-enter-from, .reasoning-panel-leave-to { opacity: 0; max-height: 0; }
```


---

### 第十一步：Agent 单元测试

**操作**：编写三组测试——AgentEvent（序列化）、内置工具（功能）、AgentController（端点）。

测试遵循项目统一的三层 Mock 策略（JUnit 5 + Mockito + MockMvc standalone），371 个测试零依赖 MySQL/Redis/网络。

#### AgentEventTest（18 个测试，纯 JUnit 5）

覆盖 6 种事件类型的 JSON 结构验证、数据完整性、边界情况（空字符串/超长内容/特殊字符/嵌套对象）、完整 Agent 对话流程模拟、前端 `dispatchEvent` 兼容性：

```text
JSON 结构完整性: 每个事件 toJson() 后 Jackson 解析验证 type/content/tool/args 等字段
数据完整性:      getType()/getData() 非空校验，done(null) 正确处理
边界情况:        空字符串 content、5000 字符超长 content、工具名含引号、嵌套 Map args
完整流程模拟:     模拟"thinking → tool_call → tool_result → token → done"完整链
前端兼容性:       用 switch-case 验证所有 type 字段与事件类型一致
```

#### WebSearchToolTest（8 个测试，Mock HttpClient）

```text
API 密钥校验:     空 API Key → error JSON；null API Key → error JSON
搜索结果处理:      HTTP 200 → 正常返回；HTTP 500 → error JSON
maxResults 限制:  maxResults=15 → 取 min(15,10)=10；null → 默认 5
异常处理:         TimeoutException → error JSON；通用 Exception → error JSON
```

#### MathToolTest（30 个测试，纯 JUnit 5）

```text
8 个基本运算:  +, -, *, /, ^, %, 优先级, 括号
12 个函数测试: sqrt, cbrt, sin, cos, tan, ln, log2, log10, abs, floor, ceil, round
3 个阶乘测试:  5!, 0!, 1!
2 个常数测试:  pi ~= 3.14159, e ~= 2.71828
6 个错误处理:  除零、非法语法(2+*3)、括号不匹配、空/null 表达式、未知函数
3 个格式测试:  整数不含小数点、含 expression 字段、JSON 合法可解析
```

#### CodeExecutionToolTest（17 个测试，兼容有/无 JS 引擎）

```text
3 个代码验证:  空代码/null → error、不支持语言(python) → error
5 个 JS 执行:  简单算术、console.log、字符串操作、数组操作、语法错误 → error
1 个环境检测:  无 JS 引擎时返回明确提示（而非 NPE）
4 个安全限制:  timeout 配置、maxOutputLength 配置、默认值验证
```

#### AgentServiceImplTest 和 AgentControllerTest（Phase 3.1 已有）

覆盖工具调用流程（Mock ChatLanguageModel + StreamingChatModel）、SSE 事件发射、无工具降级、SSE Content-Type 和事件格式验证。

**运行**：`cd springboot && mvn test` → 371 个测试，~20 秒，零失败。


---

#### Phase 3 验证清单

|# |测试场景 |预期结果 |
|---|---|---|
|1 |登录进入 AI 对话，开启 Agent 开关 |聊天界面显示 Agent 开关已激活 |
|2 |发送"帮我计算 sqrt(144) + 5!" |推理面板展示：思考 → 工具调用(calculate) → 工具结果 → 最终回答包含 √144=12, 5!=120 |
|3 |发送"搜索最新的 AI 新闻"（需配置TAVILY_API_KEY） |推理面板展示：思考 → 调用搜索工具 → 搜索结果摘要 → 流式输出新闻总结 |
|4 |发送简单问候"你好" |无工具调用，推理面板可能显示简短思考后直接输出回答 |
|5 |Agent 模式下对话超过 5 轮工具调用 |Agent 强制终止并输出已有分析 |
|6 |关闭 Agent 开关，发送同样的问题 |行为和 UI 完全回到 Phase 1 标准模式 |
|7 |开启 Agent 但关闭代码执行工具 |发送"执行 JS 计算"时 Agent 用 MathTool 替代或告知无法执行 |
|8 |点击推理面板的收起/展开 |推理面板折叠为一行摘要，点击展开恢复完整步骤列表 |
|9 |Agent 推理过程中点击停止 |SSE 流取消，已输出的 token 保留，推理步骤保留 |
|10 |切换对话 |新的对话中推理步骤清空，旧对话的推理步骤不再显示 |


---

### 第十二步：数据库迁移 V4 — 摘要表 + 会话增强

**操作**：创建 [V4__agent_tables.sql](springboot/src/main/resources/db/migration/V4__agent_tables.sql)。

```sql
-- 对话摘要表：存储 LLM 生成的对话历史摘要（长期记忆）
CREATE TABLE IF NOT EXISTS chat_conversation_summary (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '摘要 ID',
    conversation_id  BIGINT NOT NULL COMMENT '所属会话 ID',
    summary          TEXT NOT NULL COMMENT '对话摘要内容（由 LLM 生成，≤300 字）',
    message_count    INT NOT NULL COMMENT '摘要覆盖的消息数量',
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (conversation_id) REFERENCES chat_conversation(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话摘要表（Phase 3.4 长期记忆）';

CREATE INDEX idx_summary_conv ON chat_conversation_summary(conversation_id, created_at);

ALTER TABLE chat_conversation
    ADD COLUMN summary_id BIGINT DEFAULT NULL COMMENT '最新摘要 ID' AFTER deleted_at,
    ADD FOREIGN KEY fk_conv_summary (summary_id) REFERENCES chat_conversation_summary(id) ON DELETE SET NULL;
```

**设计要点**：

- `message_count` 的作用：记录每次摘要覆盖了多少条消息。后续触发的摘要只处理"未被已生成摘要覆盖"的新消息，避免重复摘要。未覆盖消息数 = 总消息数 - sum(message_count)
- `ON DELETE SET NULL`：摘要被删除时，`chat_conversation.summary_id` 自动置为 NULL，不会阻止摘要删除
- **为什么不用软删除？** 摘要是 AI 生成的中间产物，不是用户数据。如果不需要了，直接物理删除即可。而且摘要数量少（每 10 条消息才一条），不会有大量删除操作


---

### 第十三步：Redis 记忆存储 — ChatMemoryStore 实现

**操作**：创建 [RedisChatMemoryStore.java](springboot/src/main/java/com/zora/agent/memory/RedisChatMemoryStore.java)。

实现 LangChain4j 的 `ChatMemoryStore` 接口，将对话消息窗口缓存到 Redis：

```java
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {

    private static final String KEY_PREFIX = "memory:conv:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        // 从 Redis 读取 JSON → 反序列化为 ChatMessage 列表
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // 序列化 ChatMessage 列表 → 写入 Redis（TTL 24h）
    }

    @Override
    public void deleteMessages(Object memoryId) {
        // 删除 Redis key
    }

    /** 序列化策略 */
    private String serializeMessages(List<ChatMessage> messages) {
        // 每条消息存为 {"type":"USER","text":"..."}，整个列表序列化为 JSON 数组
        // 使用 Jackson ObjectMapper
    }
}
```

**设计要点**：

- **为什么序列化为** `{"type":"USER","text":"..."}`？ LangChain4j 的 ChatMessage 子类（UserMessage、AiMessage 等）没有默认 Jackson 序列化注解，无法直接用 `ObjectMapper.writeValueAsString()` 序列化。手动构建简单 JSON 结构，绕过序列化问题
- `@Component` 注解：Spring 自动扫描并注入 Bean。构造函数接收 `StringRedisTemplate`（Spring 自动注入）
- **TTL 24 小时的考量**：Redis 是"热缓存"层——活跃对话的记忆保持在 Redis 中以便快速读写。24 小时后自动清除，此时系统从 MySQL 重建历史。这与 JWT Token 的 TTL 策略一致

**踩坑记录**：

1. `ChatMessageType` 枚举有 4 个值（SYSTEM/USER/AI/TOOL_EXECUTION_RESULT），但 switch 表达式缺少 default 分支会编译报错 → 添加 `default -> ""` 兜底
2. `AiMessage.text()` 可能为 null（当 LLM 仅在消息中声明工具调用时）→ 使用三元运算符：`ai.text() != null ? ai.text() : ""`


---

### 第十四步：对话摘要服务 — ConversationSummaryService

**操作**：创建 [ConversationSummaryService.java](springboot/src/main/java/com/zora/service/ConversationSummaryService.java) 和 [ConversationSummaryServiceImpl.java](springboot/src/main/java/com/zora/service/impl/ConversationSummaryServiceImpl.java)。

#### 服务接口

```java
public interface ConversationSummaryService {
    int SUMMARY_TRIGGER_COUNT = 10;   // 每 10 条消息触发一次摘要
    int SUMMARY_MAX_LENGTH = 300;     // 摘要最大 300 字符

    /** 检查并触发摘要生成（异步） */
    void checkAndSummarize(Long conversationId);

    /** 构建摘要上下文文本（注入 System Prompt 用） */
    String buildSummaryContext(Long conversationId);
}
```

#### 核心实现

```java
@Service
public class ConversationSummaryServiceImpl implements ConversationSummaryService {

    @Override
    public void checkAndSummarize(Long conversationId) {
        // 1. 统计消息总数
        Long total = messageMapper.selectCount(...);
        // 2. 计算已覆盖消息数 = sum(历史摘要的 message_count)
        int covered = getCoveredMessageCount(conversationId);
        // 3. 未覆盖 = total - covered
        long uncovered = total - covered;
        // 4. 未达阈值 → 跳过
        if (uncovered < SUMMARY_TRIGGER_COUNT) return;
        // 5. 异步执行摘要生成
        CompletableFuture.runAsync(() -> generateAndSaveSummary(conversationId, covered, triggerCount))
                .exceptionally(ex -> { log.error(...); return null; });
    }

    private void generateAndSaveSummary(Long conversationId, int offset, int triggerCount) {
        // 1. 加载未覆盖的最近 N 条消息（LIMIT offset, N）
        List<ChatMessage> messages = loadRecentMessages(conversationId, triggerCount + offset, offset);
        // 2. 调用 LLM 生成摘要（≤300 字）
        String summary = generateSummary(messages, maxLength);
        if (summary.isEmpty()) return; // 空摘要跳过保存
        // 3. 存储摘要 + 更新会话的 summary_id
        ChatConversationSummary entity = new ChatConversationSummary(conversationId, summary, messages.size());
        summaryMapper.insert(entity);
        conversation.setSummaryId(entity.getId());
        conversationMapper.updateById(conversation);
    }

    @Override
    public String buildSummaryContext(Long conversationId) {
        List<ChatConversationSummary> summaries = summaryMapper.selectByConversationId(conversationId);
        if (summaries.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("【对话历史摘要（长期记忆，按时间顺序）】\n");
        for (int i = 0; i < summaries.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, summaries.get(i).getSummary()));
        }
        sb.append("【历史摘要结束】\n");
        return sb.toString();
    }
}
```

**设计要点**：

- **为什么用** `CompletableFuture.runAsync()` 而不是 `@Async`？ `CompletableFuture` 不需要 `@EnableAsync` 配置，代码更简单。`runAsync()` 使用 `ForkJoinPool.commonPool()`，对少量异步任务（每次对话最多触发一次）足够
- **为什么** `checkAndSummarize` 在 AI 回复保存后调用？ 这样摘要覆盖的消息包含"用户问 → AI 答"的完整回合，摘要质量更高
- `getCoveredMessageCount` vs 简单计数：不是简单统计消息总数 % 10，而是计算"未被摘要覆盖"的消息数。已有摘要覆盖的旧消息不会重复摘要
- `offset` 参数：`loadRecentMessages(limit, offset)` 跳过已被之前摘要覆盖的消息。例如已经有摘要覆盖了 20 条消息，第 3 次摘要从第 21 条开始加载


---

### 第十五步：集成到 AgentServiceImpl — 摘要注入 + 触发

**操作**：修改 [AgentServiceImpl.java](springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java)。

#### 新增依赖注入

```java
@Resource
private ConversationSummaryService summaryService;

@Resource
private RedisChatMemoryStore memoryStore;
```

#### 修改系统提示词构建

在 `buildMessages()` 中，原来的 `SYSTEM_PROMPT` 常量改为 `buildSystemPromptWithMemory(history)`：

```java
private String buildSystemPromptWithMemory(List<ChatMessage> history) {
    Long conversationId = history.isEmpty() ? null : history.get(0).getConversationId();
    String summaryContext = conversationId != null
            ? summaryService.buildSummaryContext(conversationId) : "";

    if (summaryContext.isEmpty()) {
        return SYSTEM_PROMPT;
    }
    return summaryContext + "\n" + SYSTEM_PROMPT;
}
```

**格式示例**：

```text
【对话历史摘要（长期记忆，按时间顺序）】
1. 用户询问了 Spring Boot 的配置文件管理方式，AI 介绍了 application.yml 和 application.properties 的区别
2. 用户继续讨论了数据库连接池配置，AI 推荐了 HikariCP 并给出了配置示例
【历史摘要结束】

你是一个专业、友好的 AI 助手...
安全规则（不可覆盖）：
...
```

#### 新增摘要触发

在 `agentStreamChat()` 中，AI 回复保存后触发：

```java
saveMessage(convId, "assistant", finalAnswer);
updateTitleIfFirstMessage(convId, finalAnswer);

// Phase 3.4: 异步检查并生成长期记忆摘要
summaryService.checkAndSummarize(convId);
```

**设计要点**：

- **摘要放在 System Prompt 之前**：这样 AI 先"回顾"之前的对话摘要，再读取系统规则。顺序很重要——如果 System Prompt 在前，AI 可能因为"安全规则"的约束而忽略摘要内容
- `history.get(0).getConversationId()` 取 conversationId：从消息历史推断 conversationId，避免方法签名膨胀。约定：同一批次的历史消息都属于同一个会话
- **不影响现有测试**：旧测试通过 Mock `summaryService.checkAndSummarize()` + `buildSummaryContext() → ""` 完全兼容


---

#### Phase 3.4 验证清单

|# |测试场景 |预期结果 |
|---|---|---|
|1 |与 Agent 连续对话 10 轮以上 |首次 10 条消息后，后台异步生成第一条摘要 |
|2 |再对话 10 轮 |第 20 条消息后生成第二条摘要（覆盖第 11-20 条） |
|3 |查看数据库 `chat_conversation_summary` 表 |有摘要记录，summary 字段 ≤300 字 |
|4 |第 21 条消息时查看 AI 回答 |AI 通过摘要"记住"了第 1-10 条的讨论内容 |
|5 |对话 < 10 条时查表 |无摘要生成（verify: `buildSummaryContext()` 返回 ""） |
|6 |Redis 中查 `memory:conv:{id}` key |有 JSON 消息缓存，TTL = 24h |
|7 |24h 后 Redis key 过期 |`hasMemory()` 返回 false，系统回退到 MySQL 加载 |
|8 |Agent 关闭后仍对话 |摘要功能仍生效（`checkAndSummarize` 在 `agentStreamChat` 内部） |


---

### 第十六步：AgentState + AgentNode — 图状态与节点接口

#### 操作

创建多 Agent 编排的基础类型：

**1.** `agent/graph/AgentState.java` — 图状态"黑板"：

```java
public class AgentState {
    private String userMessage;
    private List<ChatMessage> messages;
    private String intent;                          // research/math/code/general
    private String activeSpecialist;
    private List<SpecialistResult> specialistResults = new ArrayList<>();
    private int specialistCallCount = 0;
    private int maxSpecialistCalls = 3;

    public record SpecialistResult(String agentName, String toolName, String result) {}

    public void addSpecialistResult(SpecialistResult r) {
        specialistResults.add(r);
        specialistCallCount++;
    }

    public boolean canCallSpecialist() {
        return specialistCallCount < maxSpecialistCalls;
    }

    public String getResultsSummary() { ... }        // 供 Summarizer 使用
}
```

**2.** `agent/graph/AgentNode.java` — 节点统一接口：

```java
public interface AgentNode {
    String execute(AgentState state, FluxSink<String> emitter);
    String getName();
}
```

#### 设计要点

- **"黑板"模式**：Agent 节点通过共享的 `AgentState` 对象通信，完全解耦。每个节点只读写自己关心的字段，不需要知道其他节点的存在
- `SpecialistResult` 是 record：Java 16+ 支持 record 类型，编译后自动生成 equals/hashCode/toString，代码量少 80%
- `canCallSpecialist()` 封装调用限制：外部只需 `if (state.canCallSpecialist())`，不需要自行维护计数器
- `AgentNode` 接口极简：只有一个 `execute()` 方法和一个 `getName()`，任何类都可以实现成为一个图节点

#### 踩坑记录

- **无**


---

### 第十七步：SupervisorAgent + Specialist Agents — 四类 Agent 节点

#### 操作

**1.** `agent/graph/SupervisorAgent.java` — 意图分类器：

```java
public class SupervisorAgent implements AgentNode {
    private static final String CLASSIFICATION_PROMPT =
        "你是一个智能任务分类器。分析用户的消息，判断最适合由哪个专家处理：\n\n"
        + "## 专家类型\n"
        + "- research: 需要搜索互联网信息、查找最新数据\n"
        + "- math: 需要进行数学计算、数值运算\n"
        + "- code: 需要编写代码、执行程序\n"
        + "- general: 一般性问答，不需要调用工具\n\n"
        + "只回复一个单词：research、math、code 或 general。";

    @Override
    public String execute(AgentState state, FluxSink<String> emitter) {
        // 调用 LLM 分类 → 清理结果 → 写入 state.intent
        // 异常时降级为 "general"
    }
}
```

**2.** `agent/graph/ResearchAgent.java` — 研究搜索专家：

- System Prompt：强调"搜索互联网信息、归纳总结、验证来源"
- 使用 `WebSearchTool` + `ToolSpecifications.toolSpecificationsFrom()`
- 工作流程：LLM → 工具调用 → 执行搜索 → LLM 二次推理 → 输出分析报告
- 结果记录为 `SpecialistResult("research", "searchWeb", analysis)`

**3.** `agent/graph/MathAgent.java` — 数学计算专家：

- System Prompt：强调"数学计算、分步解释、展示过程"
- 使用 `MathTool` 进行安全表达式求值
- 与 ResearchAgent 相同的工作流程模式

**4.** `agent/graph/CodeAgent.java` — 代码执行专家：

- System Prompt：强调"编写代码、安全执行、结果解释"
- 使用 `CodeExecutionTool` + 5 秒超时 + 输出截断
- 仅在 `agent.tools.code-execution.enabled=true` 时可用

#### 设计要点

- **与标准 Agent 的区别**：在标准 Agent 中，一个 LLM 管理所有工具。在多 Agent 中，每个 Specialist 有专属的 System Prompt + 专属的工具，LLM 更容易做出正确的工具调用决策
- **System Prompt 决定 Agent "人格"**：ResearchAgent 的 prompt 强调"验证来源、优先权威"，MathAgent 的 prompt 强调"分步计算、展示过程"，同样的 LLM 在不同 prompt 下表现完全不同
- **反射调用工具**：Specialist 通过 `method.invoke(toolInstance, args)` 反射调用 @Tool 方法，与 AgentServiceImpl 的 `executeToolByName()` 机模式相同，但各有独立实现
- **LLM 容错**：如果 LLM 不需要工具调用（`!aiMessage.hasToolExecutionRequests()`），直接返回文本回答——这处理了"简单搜索/简单计算不需要工具"的场景

#### 踩坑记录

|问题 |原因 |解决 |
|---|---|---|
|Supervisor 返回 "research." 带句号 |LLM 有时在分类词后加标点 |清理逻辑：`replaceAll("[^a-z]", "")` 去掉所有非字母字符 |


---

### 第十八步：AgentGraph — 编排器

#### 操作

创建 `agent/graph/AgentGraph.java`：

```java
public class AgentGraph {
    private final ChatModel chatModel;
    private final StreamingChatModel streamingModel;
    private final SupervisorAgent supervisor;
    private final ResearchAgent researchAgent;
    private final MathAgent mathAgent;
    private final CodeAgent codeAgent;
    private final int maxSpecialistCalls;

    public String execute(String userMessage,
            List<ChatMessage> messages, FluxSink<String> emitter) {

        AgentState state = new AgentState();
        state.setUserMessage(userMessage);
        state.setMessages(messages);

        // Specialist 调用循环
        while (state.canCallSpecialist()) {
            String intent = supervisor.execute(state, emitter);
            if ("general".equals(intent)) break;

            AgentNode specialist = getSpecialist(intent);
            if (specialist == null) break;

            specialist.execute(state, emitter);
        }

        // Summarizer 聚合 + 流式输出
        if (state.getSpecialistCallCount() > 0) {
            return summarizeWithSpecialists(state, emitter);
        } else {
            return directStreamAnswer(state, emitter);
        }
    }
}
```

**Summarizer** 的 System Prompt：

```text
你是一个专业的 AI 助手，负责整合多个专家的分析结果，给用户一个完整、连贯的最终回答。

#### 要求
1. 整合所有专家的发现，避免简单罗列
2. 保持回答的连贯性和逻辑性
3. 用中文回答，语气专业友好
4. 不要编造专家没有提供的信息
```

#### 设计要点

- **为什么需要 cycle 循环？** 单次分类只能路由到一个 Specialist。循环允许 Supervisor 多次分析："第一次解决 math 问题后，还有 research 需求吗？"——这处理了复合任务场景
- `maxSpecialistCalls` 限制防止无限循环：如果 Supervisor 每次都返回非 general 分类，循环会在 N 次后自动停止。这是对 LLM 不确定性的防御性编程
- **Summarizer 的 System Prompt 强调"不要编造"**：因为 Summarizer 也依赖 LLM，如果专家结果不完整，LLM 可能"编造"补充内容。明确告知"不要编造"可以显著降低幻觉率
- `directStreamAnswer` 使用流式模型：当无需 Specialist 时（general 意图），直接流式输出，用户体验更好。带降级逻辑：流式失败 → 非流式 → 错误提示

#### 踩坑记录

- **暂不适用于极短消息**：如用户只说"你好"，Supervisor 分类为 general，触发 `directStreamAnswer`，流程正常但略重。后续可优化：消息长度 < N 时直接跳过分类


---

### 第十九步：集成到 AgentServiceImpl

#### 操作

**1. 注入 Specialist 工具 Bean**：

```java
@Autowired(required = false)
private WebSearchTool webSearchTool;

@Autowired(required = false)
private MathTool mathTool;

@Autowired(required = false)
private CodeExecutionTool codeExecutionTool;
```

**2. 在** `agentStreamChat()` 中添加多 Agent 分支：

```java
if (agentConfig.getMultiAgent().isEnabled() && hasToolsForMultiAgent()) {
    // ===== 多 Agent 编排模式 =====
    finalAnswer = runMultiAgentGraph(messages, userMessage, emitter);
} else if (!enabledTools.isEmpty()) {
    // ===== 标准 Agent 模式 =====
    finalAnswer = runAgentLoop(messages, enabledTools, emitter);
} else {
    // ===== 降级模式 =====
    finalAnswer = generateDirectAnswer(messages, emitter);
}
```

**3. AgentEvent 增强**：添加 `withField()` 方法支持多 Agent 标记：

```java
emitter.next(AgentEvent.thinking("ResearchAgent 正在搜索...")
        .withField("agent", "research").toJson());
```

#### 设计要点

- **三层降级策略**：多 Agent → 标准 Agent → 直接回答。每一层都是下一层的 fallback，确保任何异常都不会导致"无回答"
- `hasToolsForMultiAgent()` 检查：即使多 Agent 配置启用，如果没有任何工具 Bean（例如未配置 Tavily API Key），也会自动降级到标准模式
- `withField()` 不破坏现有协议：前端不认识 `agent` 字段时会忽略它，完全向后兼容
- `required = false` 防止启动失败：如果 CodeExecutionTool 未启用（默认），其 Bean 不存在，用 `required = false` 避免 Spring 启动报错

#### 踩坑记录

- **无**


---

#### Phase 3.5 验证清单

|# |测试场景 |预期结果 |
|---|---|---|
|1 |发送"搜索最新的 AI 新闻" |Supervisor 分类为 research → ResearchAgent 调用 WebSearchTool → Summarizer 聚合回答 |
|2 |发送"计算 sqrt(256) * 3.14" |Supervisor 分类为 math → MathAgent 调用 calculate → 返回计算结果 |
|3 |发送"用 JS 算斐波那契数列第 10 项" |Supervisor 分类为 code → CodeAgent 执行代码 → 返回结果（需启用工具） |
|4 |发送"你好，介绍一下你自己" |Supervisor 分类为 general → 直接流式回答（无 Specialist 参与） |
|5 |配置 `agent.multi-agent.enabled=false` |多 Agent 关闭，走标准 Agent 流程 |
|6 |不配置 Tavily API Key |WebSearchTool 为 null → hasToolsForMultiAgent() 返回 false → 降级到标准模式 |
|7 |多 Agent 编排中 LLM 调用失败 |AgentGraph 异常 → 降级到 runAgentLoop → 再失败降级到 generateFallbackAnswer |
|8 |运行 `mvn test` |371 个测试全部通过（新增 36 个多 Agent 编排测试） |


---


---

### 第二十步：让 Phase 3 项目跑起来

#### 操作

Phase 3 的所有功能已经开发完毕，现在需要完成配置和启动步骤，让 AI Agent 智能体真正跑起来。

#### 1. 配置 `.env` 文件

在项目根目录的 `.env` 文件中，确保以下配置存在：

```env
# ==================== AI 扩展配置（Phase 1 启用）====================
# DeepSeek API 密钥 — 必填，否则 AI 对话无法工作
# 获取地址: https://platform.deepseek.com/api_keys
AI_API_KEY=sk-your-deepseek-api-key
AI_BASE_URL=https://api.deepseek.com/v1
AI_MODEL_NAME=deepseek-v4-flash
AI_TEMPERATURE=0.7
AI_MAX_TOKENS=4096
AI_TIMEOUT_SECONDS=120

# ==================== RAG 知识库配置（Phase 2）====================
# 嵌入模型 API Key — 使用硅基流动（国内，便宜）或 OpenAI
# 硅基流动注册: https://siliconflow.cn → API Key 管理
AI_EMBEDDING_API_KEY=sk-your-embedding-api-key
AI_EMBEDDING_BASE_URL=https://api.siliconflow.cn/v1
AI_EMBEDDING_MODEL=BAAI/bge-m3

# ==================== AI Agent 智能体配置（Phase 3）====================
# 工具开关
AGENT_TOOL_WEB_SEARCH=true       # 网页搜索（默认 true）
AGENT_TOOL_MATH=true             # 数学计算（默认 true）
AGENT_TOOL_CODE_EXEC=false       # 代码执行（默认 false，安全考量）

# Tavily 搜索 API — WebSearchTool 需要
# 注册地址: https://tavily.com → 免费 1000 次/月
# 留空则搜索工具不可用，Agent 降级到无搜索模式
TAVILY_API_KEY=tvly-your-tavily-api-key

# 多 Agent 协作模式（Phase 3.5，可选）
# 设为 true 启用 Supervisor → Specialist 协作模式
AGENT_MULTI_AGENT=false
```

**各环境变量说明**：

|变量 |必填 |默认值 |说明 |
|---|---|---|---|
|`AI_API_KEY` |✅ |— |DeepSeek API 密钥，不配则 AI 对话完全不可用 |
|`AI_BASE_URL` |❌ |`https://api.deepseek.com/v1` |AI 模型 API 地址 |
|`AI_MODEL_NAME` |❌ |`deepseek-v4-flash` |模型名称，可改为 `deepseek-v4-pro`（思考模式） |
|`AI_EMBEDDING_API_KEY` |RAG |— |Embedding 模型 Key，RAG 知识库需要 |
|`AI_EMBEDDING_BASE_URL` |❌ |`https://api.openai.com/v1` |Embedding API 地址 |
|`AI_EMBEDDING_MODEL` |❌ |`text-embedding-3-small` |Embedding 模型名 |
|`AGENT_TOOL_WEB_SEARCH` |❌ |`true` |是否启用 WebSearchTool |
|`AGENT_TOOL_MATH` |❌ |`true` |是否启用 MathTool |
|`AGENT_TOOL_CODE_EXEC` |❌ |`false` |是否启用 CodeExecutionTool（安全考量） |
|`TAVILY_API_KEY` |搜索 |— |Tavily API Key，WebSearchTool 需要 |
|`AGENT_MULTI_AGENT` |❌ |`false` |是否启用多 Agent 协作模式 |

#### 2. 启动基础设施

```bash
# 在项目根目录
cd "f:/Code/Front-end and back-end separation project"

# 启动 MySQL + Redis（如果还没有启动）
docker compose up -d mysql redis
```

#### 3. 数据库迁移（Phase 3.4 新增表）

Phase 3.4 新增了 `chat_conversation_summary` 表，需要执行数据库迁移。

**方式 A：Flyway 自动迁移（推荐）**

后端启动时，Flyway 会自动执行 `db/migration/` 下的 SQL 文件。只需确保 `application.yml` 中 Flyway 配置正确（项目默认已配置）。

**方式 B：手动执行 SQL**

如果 Flyway 未启用，手动执行：

```sql
-- 连接 MySQL（端口 13307 或你的配置）
USE springboot_zyt;

-- 执行 Phase 3.4 迁移脚本
SOURCE springboot/src/main/resources/db/migration/V4__agent_tables.sql;
```

V4 迁移脚本内容：

```sql
-- 创建对话摘要表
CREATE TABLE IF NOT EXISTS chat_conversation_summary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    summary TEXT NOT NULL,
    message_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_summary_conv (conversation_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 对话表添加摘要外键
ALTER TABLE chat_conversation ADD COLUMN IF NOT EXISTS summary_id BIGINT NULL;
ALTER TABLE chat_conversation ADD CONSTRAINT IF NOT EXISTS fk_conversation_summary
    FOREIGN KEY (summary_id) REFERENCES chat_conversation_summary(id) ON DELETE SET NULL;
```

#### 4. 启动后端

```bash
cd springboot

# 方式 A：命令行启动
mvn spring-boot:run

# 方式 B：IDEA 启动
# 直接运行 AppStart.java（推荐 IDEA 中配置 Build project automatically）
```

**启动日志检查**：

```text
✅ 启动成功：
   Started AppStart in X.XXX seconds
   Flyway: Successfully applied 1 migration to schema `springboot_zyt`

❌ 常见错误：
   "Access denied for user"  → MySQL 密码不正确（检查 .env 中 MYSQL_PASSWORD）
   "Connection refused"      → MySQL/Redis 未启动（docker compose up -d mysql redis）
   "Unknown database"        → 数据库不存在（检查 MYSQL_DATABASE=springboot_zyt）
```

#### 5. 启动前端

```bash
cd web/frontend
npm install       # 首次运行需要
npm run dev       # → http://localhost:3000
```

#### 6. 访问和验证

|服务 |地址 |说明 |
|---|---|---|
|前端页面 |`http://localhost:3000` |Vue3 前端 |
|后端 API |`http://localhost:8080` |Spring Boot 后端 |
|API 文档 |`http://localhost:3000/doc.html` |Knife4j/Swagger 文档（代理到后端） |

#### 设计要点

- **.env 文件优先级**：Spring Boot 通过 `${环境变量:默认值}` 语法读取。`.env` 文件中的值会覆盖 `application.yml` 中的默认值。Docker Compose 自动读取项目根目录的 `.env`
- **Agent 三模式切换**：
  - `AGENT_MULTI_AGENT=false` + 工具启用 → **标准 Agent 模式**（ReAct 推理循环，Phase 3.1 默认模式）
  - `AGENT_MULTI_AGENT=true` + 工具启用 → **多 Agent 模式**（Supervisor + Specialist 协作，Phase 3.5）
  - 工具全部禁用或未配置 → **增强对话模式**（AgentEvent 协议传输，但无工具调用）
- **Tavily API Key 是可选的**：没有 Key 时 WebSearchTool 不可用，但数学和代码工具仍可正常工作。Agent 会自动降级到可用工具
- **CodeExecutionTool 默认关闭**：这是安全考量。开启后，用户可以通过 Agent 执行 JavaScript 代码。在内网/开发环境可以开启，生产环境建议保持关闭

#### 踩坑记录

|问题 |原因 |解决 |
|---|---|---|
|Agent 不调用搜索工具 |Tavily API Key 未配置 |在 `.env` 中设置 `TAVILY_API_KEY` |
|Agent 不调用数学工具 |MathTool Bean 不存在 |检查 `AGENT_TOOL_MATH=true` 并重启后端 |
|前端 Agent 开关打开后无反应 |后端 `agent.multi-agent.enabled` 配置冲突 |检查 `.env` 和 `application.yml` 中的配置是否一致 |
|Agent 响应很慢（>30s） |DeepSeek API 响应慢或超时 |增加 `AI_TIMEOUT_SECONDS` 或检查网络 |
|`ChatConversationSummary` 表不存在 |V4 迁移脚本未执行 |手动执行 SQL 或确认 Flyway 启用 |
|多 Agent 模式下前端推理面板无 agent 标签 |旧版本前端不认识 `agent` 字段 |前端已兼容，`agent` 字段会被忽略 |


---

#### Phase 3 启动验证清单

|# |验证步骤 |操作 |预期结果 |
|---|---|---|---|
|1 |后端启动 |`mvn spring-boot:run` |无报错，`Started AppStart` |
|2 |前端启动 |`npm run dev` |`http://localhost:3000` 可访问 |
|3 |登录 |输入邮箱+密码 |登录成功，进入 Chat 页面 |
|4 |Agent 开关 |打开 "Agent 模式" 开关 |开关变为蓝色激活状态 |
|5 |测试数学计算 |发送"计算 sqrt(144) + 30" |推理面板显示 thinking → tool_call(calculate) → tool_result → token |
|6 |测试搜索（需 Tavily Key） |发送"搜索最新 AI 新闻" |推理面板显示 thinking → tool_call(searchWeb) → tool_result → token |
|7 |测试通用问答 |发送"你好，介绍一下你自己" |直接 token 输出，无工具调用 |
|8 |测试记忆系统 |与 Agent 连续对话 10 轮以上 |后台异步生成摘要（可查 `chat_conversation_summary` 表） |
|9 |测试多 Agent（可选） |设置 `AGENT_MULTI_AGENT=true`，重启后端，发送"搜索并计算" |推理面板显示 Supervisor → Specialist → Summarizer 流程 |
|10 |运行测试 |`mvn test` |371 个测试全部通过 |


---

## Phase 3 总结

### Phase 3 整体架构

```text
用户消息 → AgentController
  └── AgentServiceImpl.agentStreamChat()
        ├── 限流 + 注入检测（同 Phase 1/2）
        ├── 用户查找 + 对话管理
        ├── 模式判断：
        │   ├── agent.multi-agent.enabled=true
        │   │   └── AgentGraph.execute()
        │   │       ├── SupervisorAgent.classify()
        │   │       ├── ResearchAgent/MathAgent/CodeAgent（按意图路由）
        │   │       └── Summarizer.aggregate() → 流式输出
        │   ├── tools 非空
        │   │   └── runAgentLoop()（ReAct 循环 + 工具调用）
        │   └── tools 为空
        │       └── generateDirectAnswer() → 流式输出
        └── 保存消息 + 触发摘要生成（Phase 3.4）
```

### Phase 3 各子阶段测试统计

|子阶段 |新增测试数 |累计测试数 |新增类 |
|---|---|---|---|
|3.1 基础框架 |+33 |245 |7 (+2 修改) |
|3.2 内置工具 |+55 |300 |3 (+2 修改) |
|3.3 可视化推理 |+18 |311 |2 (+1 修改) |
|3.4 记忆系统 |+24 |335 |5 (+3 修改) |
|**3.5 多 Agent 编排** |**+36** |**371** |**8 (+2 修改)** |

### Phase 3 新增 API

|Method |Path |Phase |Description |
|---|---|---|---|
|POST |`/agent/chat/stream` |3.1 |Agent SSE 流式对话（支持工具调用、多 Agent 编排） |

> 详细 SSE 事件协议和架构说明参见 [CLAUDE.md](CLAUDE.md) 的"AI Agent 智能体 (Phase 3)"章节。

## Phase 3 完整文件清单

### 新增文件

|文件路径 |行数 |说明 |
|---|---|---|
|`springboot/src/main/java/com/zora/agent/AgentService.java` |~42 |Agent 服务接口 |
|`springboot/src/main/java/com/zora/agent/impl/AgentServiceImpl.java` |~950 |核心实现：两阶段流式 + ReAct + 多 Agent |
|`springboot/src/main/java/com/zora/agent/event/AgentEvent.java` |~185 |结构化 SSE 事件 + withField 增强 |
|`springboot/src/main/java/com/zora/agent/tool/Tool.java` |~32 |工具标记接口 |
|`springboot/src/main/java/com/zora/agent/tool/WebSearchTool.java` |~152 |Tavily 网页搜索工具 |
|`springboot/src/main/java/com/zora/agent/tool/MathTool.java` |~270 |exp4j 数学计算工具 |
|`springboot/src/main/java/com/zora/agent/tool/CodeExecutionTool.java` |~230 |JS 沙箱代码执行工具 |
|`springboot/src/main/java/com/zora/agent/memory/RedisChatMemoryStore.java` |~230 |LangChain4j ChatMemoryStore Redis 实现 |
|`springboot/src/main/java/com/zora/agent/graph/AgentState.java` |~105 |图状态"黑板" + SpecialistResult record |
|`springboot/src/main/java/com/zora/agent/graph/AgentNode.java` |~45 |节点统一接口 |
|`springboot/src/main/java/com/zora/agent/graph/SupervisorAgent.java` |~155 |LLM 零样本意图分类器 |
|`springboot/src/main/java/com/zora/agent/graph/ResearchAgent.java` |~175 |研究搜索专家（WebSearchTool） |
|`springboot/src/main/java/com/zora/agent/graph/MathAgent.java` |~170 |数学计算专家（MathTool） |
|`springboot/src/main/java/com/zora/agent/graph/CodeAgent.java` |~170 |代码执行专家（CodeExecutionTool） |
|`springboot/src/main/java/com/zora/agent/graph/AgentGraph.java` |~280 |多 Agent 编排器（Supervisor + Summarizer） |
|`springboot/src/main/java/com/zora/entity/AgentStep.java` |~77 |推理步骤 POJO |
|`springboot/src/main/java/com/zora/entity/ChatConversationSummary.java` |~65 |对话摘要实体 |
|`springboot/src/main/java/com/zora/mapper/ChatConversationSummaryMapper.java` |~45 |摘要 Mapper |
|`springboot/src/main/java/com/zora/service/ConversationSummaryService.java` |~40 |摘要服务接口 |
|`springboot/src/main/java/com/zora/service/impl/ConversationSummaryServiceImpl.java` |~240 |异步摘要生成实现 |
|`springboot/src/main/java/com/zora/config/AgentConfig.java` |~178 |Agent 全部配置映射 |
|`springboot/src/main/java/com/zora/config/AiConfig.java` |(修改) |+非流式 ChatModel bean |
|`springboot/src/main/java/com/zora/controller/AgentController.java` |~109 |/agent/chat/stream SSE 端点 |
|`springboot/src/main/resources/db/migration/V4__agent_tables.sql` |~18 |chat_conversation_summary 建表 |
|`web/frontend/src/api/agent.js` |~80 |Agent SSE 客户端 |
|`web/frontend/src/views/Chat.vue` |(修改) |+Agent 开关、推理面板、三模式分发 |

### 测试文件

|文件路径 |测试数 |说明 |
|---|---|---|
|`AgentEventTest.java` |22 |SSE 事件序列化 + withField 测试 |
|`AgentServiceImplTest.java` |7 |限流/注入/用户查找/SSE 事件 |
|`WebSearchToolTest.java` |18 |搜索工具全部场景 |
|`MathToolTest.java` |20 |数学计算全部场景 |
|`CodeExecutionToolTest.java` |18 |代码执行全部场景 |
|`RedisChatMemoryStoreTest.java` |13 |记忆存储 CRUD + 序列化 |
|`ConversationSummaryServiceTest.java` |11 |摘要触发逻辑 + 异步 |
|`AgentStateTest.java` |15 |图状态管理 |
|`SupervisorAgentTest.java` |9 |意图分类 |
|`AgentGraphTest.java` |8 |多 Agent 编排 |
|**合计** |**36** |Phase 3.5 新增测试（总计 371） |

> 前端文件 `api/agent.js` 和 `views/Chat.vue` 的详细代码说明参见 CLAUDE.md 的"Agent 可视化推理过程"和"Agent 记忆系统"章节。


---