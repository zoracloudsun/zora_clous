package com.zyt.service;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * 调试 Embedding API 调用，定位 20015 错误根因
 */
public class EmbeddingDebugTest {

    @Test
    public void testSimpleEmbedding() {
        EmbeddingModel model = OpenAiEmbeddingModel.builder()
                .apiKey("sk-wlxhawfyerfptdkgwsubsnbtfywbynujydeelkxvhxrbivww")
                .baseUrl("https://api.siliconflow.cn/v1")
                .modelName("BAAI/bge-large-zh-v1.5")
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();

        // 测试简单中文文本
        System.out.println("=== 测试1: 简单中文 ===");
        try {
            var response = model.embed("测试一段简单的文本");
            System.out.println("SUCCESS: " + response.content().vector().length + " dims");
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
        }

        // 测试较长文本（模拟 PDF 分块）
        String longText = "T E C H\n\n应届生求职全攻略：秋招、实习与找工作详解\n\n"
                + "2026年6月5日\n\n"
                + "作为2027届软件工程本科应届生，当前正处于求职周期的关键交汇点：暑期实习申请\n\n"
                + "进入最后窗口期，秋招提前批即将启动。";

        System.out.println("\n=== 测试2: 较长PDF文本（" + longText.length() + " chars）===");
        try {
            var response = model.embed(longText);
            System.out.println("SUCCESS: " + response.content().vector().length + " dims");
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
        }
    }
}
