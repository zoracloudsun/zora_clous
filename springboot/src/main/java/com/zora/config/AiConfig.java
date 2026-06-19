package com.zora.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置类
 * <p>
 * 创建 LangChain4j 的聊天模型 Bean，指向 DeepSeek API（兼容 OpenAI 格式）。
 * 提供两个 Bean：
 * <ul>
 * <li>{@code streamingChatLanguageModel} — 流式模型，用于 SSE 实时输出</li>
 * <li>{@code chatLanguageModel} — 非流式模型，用于 Agent 工具调用推理</li>
 * </ul>
 * </p>
 */
@Configuration
public class AiConfig {

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${ai.model-name:deepseek-chat}")
    private String modelName;

    @Value("${ai.temperature:0.7}")
    private Double temperature;

    @Value("${ai.max-tokens:4096}")
    private Integer maxTokens;

    @Value("${ai.timeout-seconds:120}")
    private Integer timeoutSeconds;

    /**
     * 流式聊天模型 Bean（Phase 1）
     * <p>
     * 用于 SSE 流式对话：每个 token 通过 {@code onPartialResponse} 回调实时推送到前端。
     * DeepSeek 完全兼容 OpenAI API 格式，直接使用 OpenAiStreamingChatModel。
     * </p>
     *
     * @return StreamingChatLanguageModel 实例
     */
    @Bean
    public OpenAiStreamingChatModel streamingChatLanguageModel() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 非流式聊天模型 Bean（Phase 3 Agent）
     * <p>
     * 用于 Agent 推理循环中的工具调用判断。
     * 因为 DeepSeek（以及大多数模型）不能在流式模式下同时进行 function calling，
     * Agent 需要先通过非流式调用完成"思考 → 工具调用 → 结果分析"循环，
     * 最后再通过流式模型输出最终回答。
     * </p>
     *
     * @return ChatModel 实例
     */
    @Bean
    public ChatModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
