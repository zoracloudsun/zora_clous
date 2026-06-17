package com.zora.config;

import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置类
 * 创建 LangChain4j 的 StreamingChatLanguageModel Bean，
 * 指向 DeepSeek API（兼容 OpenAI 格式）
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
     * 流式聊天模型 Bean
     * DeepSeek 完全兼容 OpenAI API 格式，直接使用 OpenAiStreamingChatModel
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
}
