package com.zyt.service.impl;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.*;
import dev.langchain4j.store.embedding.filter.Filter;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简单内存向量存储（Phase 2: RAG 知识库）
 * <p>
 * 自实现的基于余弦相似度的内存向量存储。
 * 因为 langchain4j 1.15.0 的 {@code InMemoryEmbeddingStore} 仅在 aggregator 包中，
 * 不在 langchain4j-core 或 langchain4j-open-ai 中，所以自实现一个轻量版本。
 * </p>
 *
 * <h3>实现说明</h3>
 * <ul>
 * <li>使用 {@link CopyOnWriteArrayList} 存储 (Embedding, TextSegment) 对，读多写少场景</li>
 * <li>搜索时计算查询向量与所有存储向量的余弦相似度</li>
 * <li>按分数降序排序，取 top-N 结果</li>
 * <li>支持 minScore 阈值过滤</li>
 * </ul>
 */
public class SimpleEmbeddingStore implements EmbeddingStore<TextSegment> {

    /** 存储条目：嵌入向量 + 文本段 */
    private static class Entry {
        final String id;
        final Embedding embedding;
        final TextSegment segment;

        Entry(String id, Embedding embedding, TextSegment segment) {
            this.id = id;
            this.embedding = embedding;
            this.segment = segment;
        }
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    @Override
    public String add(Embedding embedding) {
        String id = UUID.randomUUID().toString();
        entries.add(new Entry(id, embedding, null));
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        entries.add(new Entry(id, embedding, null));
    }

    @Override
    public String add(Embedding embedding, TextSegment segment) {
        String id = UUID.randomUUID().toString();
        entries.add(new Entry(id, embedding, segment));
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>();
        for (Embedding e : embeddings) {
            ids.add(add(e));
        }
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            TextSegment seg = (segments != null && i < segments.size()) ? segments.get(i) : null;
            ids.add(add(embeddings.get(i), seg));
        }
        return ids;
    }

    @Override
    public void remove(String id) {
        entries.removeIf(e -> e.id.equals(id));
    }

    @Override
    public void removeAll() {
        entries.clear();
    }

    @Override
    public void removeAll(Filter filter) {
        // 简化实现：不支持复杂过滤
        entries.clear();
    }

    @Override
    public void removeAll(Collection<String> ids) {
        entries.removeIf(e -> ids.contains(e.id));
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding queryEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();

        // 计算所有条目的余弦相似度
        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
        for (Entry entry : entries) {
            double score = cosineSimilarity(queryEmbedding, entry.embedding);
            if (score >= minScore) {
                matches.add(new EmbeddingMatch<>(score, entry.id, entry.embedding, entry.segment));
            }
        }

        // 按分数降序排序
        matches.sort((a, b) -> Double.compare(b.score(), a.score()));

        // 取 top-N
        if (matches.size() > maxResults) {
            matches = matches.subList(0, maxResults);
        }

        return new EmbeddingSearchResult<>(matches);
    }

    /**
     * 计算两个向量的余弦相似度
     * <p>
     * CosineSimilarity = dot(A, B) / (||A|| * ||B||)
     * 结果范围 [-1, 1]，越接近 1 表示越相似。
     * </p>
     */
    private static double cosineSimilarity(Embedding a, Embedding b) {
        float[] va = a.vector();
        float[] vb = b.vector();
        if (va.length != vb.length) {
            return 0.0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < va.length; i++) {
            dot += (double) va[i] * vb[i];
            normA += (double) va[i] * va[i];
            normB += (double) vb[i] * vb[i];
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 根据文档 ID 移除所有关联的向量条目
     * <p>
     * 遍历存储中的所有条目，通过 {@link TextSegment} 的 metadata 中的
     * {@code document_id} 字段匹配目标文档，移除所有匹配的条目。
     * 用于回收站操作（永久删除时清理向量、恢复时清除残留向量）。
     * </p>
     *
     * @param documentId 文档 ID，对应 metadata 中的 "document_id" 字段
     * @return 实际移除的条目数量
     */
    public int removeByDocumentId(Long documentId) {
        String target = String.valueOf(documentId);
        int before = entries.size();
        entries.removeIf(e -> {
            if (e.segment == null || e.segment.metadata() == null) return false;
            Object docId = e.segment.metadata().toMap().get("document_id");
            return docId != null && target.equals(String.valueOf(docId));
        });
        return before - entries.size();
    }

    /** 获取存储的条目数（用于调试和测试） */
    public int size() {
        return entries.size();
    }
}
