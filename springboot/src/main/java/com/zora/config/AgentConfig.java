package com.zora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 智能体配置类（Phase 3）
 * <p>
 * 将 application.yml 中 {@code agent.*} 配置段映射为类型安全的 Java 对象。
 * 使用 {@code @ConfigurationProperties} 实现自动绑定，支持 IDE 自动补全和校验。
 * </p>
 *
 * <h3>配置结构</h3>
 * <pre>{@code
 * agent:
 *   tools:
 *     web-search:
 *       enabled: true
 *     math:
 *       enabled: true
 *     code-execution:
 *       enabled: false
 *       timeout-seconds: 5
 *       max-output-length: 10000
 *   tavily:
 *     api-key: xxx
 *     base-url: https://api.tavily.com/search
 *     timeout-seconds: 15
 *   multi-agent:
 *     enabled: false
 *     max-specialist-calls: 3
 *   memory:
 *     window-size: 20
 *     summary-trigger-count: 10
 *     summary-max-length: 300
 * }</pre>
 */
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentConfig {

    /** 工具统一配置 */
    private ToolsConfig tools = new ToolsConfig();

    /** Tavily 搜索 API 配置 */
    private TavilyConfig tavily = new TavilyConfig();

    /** 多智能体编排配置（Phase 3.5） */
    private MultiAgentConfig multiAgent = new MultiAgentConfig();

    /** 记忆系统配置（Phase 3.4） */
    private MemoryConfig memory = new MemoryConfig();

    // ==================== 内部配置类 ====================

    /**
     * 工具开关配置
     */
    public static class ToolsConfig {
        /** 网页搜索工具配置 */
        private ToolSwitch webSearch = new ToolSwitch();

        /** 数学计算工具配置 */
        private ToolSwitch math = new ToolSwitch();

        /** 代码执行工具配置 */
        private CodeExecConfig codeExecution = new CodeExecConfig();

        public ToolSwitch getWebSearch() { return webSearch; }
        public void setWebSearch(ToolSwitch webSearch) { this.webSearch = webSearch; }
        public ToolSwitch getMath() { return math; }
        public void setMath(ToolSwitch math) { this.math = math; }
        public CodeExecConfig getCodeExecution() { return codeExecution; }
        public void setCodeExecution(CodeExecConfig codeExecution) { this.codeExecution = codeExecution; }
    }

    /**
     * 单个工具开关（通用）
     */
    public static class ToolSwitch {
        /** 是否启用该工具 */
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    /**
     * 代码执行工具专有配置
     */
    public static class CodeExecConfig {
        /** 是否启用代码执行工具（默认关闭，安全考量） */
        private boolean enabled = false;

        /** 代码执行超时（秒） */
        private int timeoutSeconds = 5;

        /** 输出最大字符数 */
        private int maxOutputLength = 10000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxOutputLength() { return maxOutputLength; }
        public void setMaxOutputLength(int maxOutputLength) { this.maxOutputLength = maxOutputLength; }
    }

    /**
     * Tavily 搜索 API 配置
     */
    public static class TavilyConfig {
        /** Tavily API 密钥 */
        private String apiKey = "";

        /** Tavily API 基础 URL */
        private String baseUrl = "https://api.tavily.com/search";

        /** 搜索请求超时时间（秒） */
        private int timeoutSeconds = 15;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    /**
     * 多智能体编排配置（Phase 3.5 启用）
     */
    public static class MultiAgentConfig {
        /** 是否启用多 Agent 协作模式 */
        private boolean enabled = false;

        /** 单次对话最多调用的 Specialist Agent 次数 */
        private int maxSpecialistCalls = 3;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxSpecialistCalls() { return maxSpecialistCalls; }
        public void setMaxSpecialistCalls(int maxSpecialistCalls) { this.maxSpecialistCalls = maxSpecialistCalls; }
    }

    /**
     * 记忆系统配置（Phase 3.4 启用）
     */
    public static class MemoryConfig {
        /** 短期记忆窗口大小（消息数） */
        private int windowSize = 20;

        /** 触发摘要生成的消息数阈值 */
        private int summaryTriggerCount = 10;

        /** 摘要最大字符数 */
        private int summaryMaxLength = 300;

        public int getWindowSize() { return windowSize; }
        public void setWindowSize(int windowSize) { this.windowSize = windowSize; }
        public int getSummaryTriggerCount() { return summaryTriggerCount; }
        public void setSummaryTriggerCount(int summaryTriggerCount) { this.summaryTriggerCount = summaryTriggerCount; }
        public int getSummaryMaxLength() { return summaryMaxLength; }
        public void setSummaryMaxLength(int summaryMaxLength) { this.summaryMaxLength = summaryMaxLength; }
    }

    // ==================== getter / setter ====================

    public ToolsConfig getTools() { return tools; }
    public void setTools(ToolsConfig tools) { this.tools = tools; }
    public TavilyConfig getTavily() { return tavily; }
    public void setTavily(TavilyConfig tavily) { this.tavily = tavily; }
    public MultiAgentConfig getMultiAgent() { return multiAgent; }
    public void setMultiAgent(MultiAgentConfig multiAgent) { this.multiAgent = multiAgent; }
    public MemoryConfig getMemory() { return memory; }
    public void setMemory(MemoryConfig memory) { this.memory = memory; }
}
