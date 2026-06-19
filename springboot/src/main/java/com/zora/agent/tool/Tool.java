package com.zora.agent.tool;

/**
 * Agent 工具标记接口（Phase 3）
 * <p>
 * 所有 Agent 工具必须实现此接口，以便通过 Spring 的 {@code List<Tool>} 自动发现和注入。
 * 配合 LangChain4j 的 {@code @Tool} 注解使用：
 * </p>
 *
 * <pre>{@code
 * @Component
 * public class MyTool implements Tool {
 *     @Tool("工具的功能描述，LLM 会根据此描述决定何时调用")
 *     public String myMethod(@P("参数说明") String param) {
 *         // 工具实现
 *         return result;
 *     }
 * }
 * }</pre>
 *
 * <p>
 * AgentServiceImpl 通过 {@code @Resource private List<Tool> tools} 自动收集所有工具 Bean，
 * 然后根据 {@code agent.tools.*.enabled} 配置过滤后传递给 {@code AiServices}。
 * </p>
 *
 * @see com.zora.agent.impl.AgentServiceImpl
 */
public interface Tool {
    // 标记接口，无需定义方法
    // 工具方法通过 LangChain4j 的 @Tool 注解声明
}
