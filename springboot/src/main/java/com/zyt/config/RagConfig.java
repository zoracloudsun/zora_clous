package com.zyt.config;

import com.zyt.service.impl.SimpleEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * RAG 知识库配置类（Phase 2）
 * <p>
 * 创建嵌入模型和向量存储 Bean：
 * <ul>
 * <li>{@link EmbeddingModel} — OpenAI 兼容的嵌入模型（文本 → 向量）</li>
 * <li>{@link SimpleEmbeddingStore} — 自实现的内存向量存储（应用重启后从 MySQL 重建）</li>
 * </ul>
 * </p>
 *
 * <h3>嵌入模型选择</h3>
 * DeepSeek 不提供公开的 Embedding API，因此使用单独的嵌入模型服务。
 * 支持任何 OpenAI 兼容的嵌入 API：OpenAI / 硅基流动 / Ollama 等。
 * 配置通过 {@code application.yml} 的 {@code rag.embedding.*} 指定。
 *
 * <h3>向量存储选择</h3>
 * 使用自实现的 {@link SimpleEmbeddingStore}（基于余弦相似度的内存向量存储），
 * 因为 langchain4j 1.15.0 的 {@code InMemoryEmbeddingStore} 仅在 aggregator 模块中可用。
 * 自实现版本功能等效且更轻量。
 */
@Configuration
public class RagConfig {

    @Value("${rag.embedding.api-key}")
    private String embeddingApiKey;

    @Value("${rag.embedding.base-url:https://api.openai.com/v1}")
    private String embeddingBaseUrl;

    @Value("${rag.embedding.model-name:text-embedding-3-small}")
    private String embeddingModelName;

    /**
     * 嵌入模型 Bean
     * <p>
     * 复用 langchain4j-open-ai 的 {@link OpenAiEmbeddingModel}，
     * 与现有的 {@code OpenAiStreamingChatModel}（DeepSeek 聊天模型）使用相同的适配器模式。
     * baseUrl 可指向 OpenAI、硅基流动、Ollama 等任何兼容服务。
     * </p>
     */
    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .apiKey(embeddingApiKey)
                .baseUrl(embeddingBaseUrl)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    /**
     * 内存向量存储 Bean
     * <p>
     * 使用自实现的 {@link SimpleEmbeddingStore}，基于余弦相似度的内存向量检索。
     * 数据在应用重启后丢失，通过
     * {@code RagProcessingServiceImpl.rebuildEmbeddingStore()}
     * 从 MySQL {@code kb_chunk} 表重建所有向量索引。
     * </p>
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new SimpleEmbeddingStore();
    }
}
