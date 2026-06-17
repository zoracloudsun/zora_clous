package com.zyt.controller;

import com.zyt.service.RagService;
import com.zyt.utils.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * RAG 知识库控制器（Phase 2）
 * <p>
 * 提供知识库 CRUD、文档上传管理和知识检索的 REST API。
 * 所有端点需要登录（由 LoginInterceptor 校验），知识库按用户隔离。
 * </p>
 */
@RestController
@RequestMapping("/rag")
@Tag(name = "RAG 知识库", description = "知识库管理、文档上传、向量检索查询")
public class RagController {

    /** 文件上传最大大小（10MB，与后端配置 double-check） */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Resource
    private RagService ragService;

    // ==================== 知识库 CRUD ====================

    @Operation(summary = "创建知识库", description = "创建一个新的知识库，用于存放文档。名称不能为空且不超过200字符。")
    @PostMapping("/knowledge-bases")
    public ResponseUtil createKnowledgeBase(
            @RequestBody Map<String, String> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        String name = body.get("name");
        String description = body.get("description");
        Map<String, Object> result = ragService.createKnowledgeBase(email, name, description);
        return ResponseUtil.success("知识库创建成功", result);
    }

    @Operation(summary = "获取知识库列表", description = "获取当前用户的所有知识库，按最近更新时间倒序排列。" +
            "每个知识库返回文档数量统计。")
    @GetMapping("/knowledge-bases")
    public ResponseUtil listKnowledgeBases(
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Map<String, Object>> result = ragService.listKnowledgeBases(email);
        return ResponseUtil.success(result);
    }

    @Operation(summary = "获取知识库详情", description = "获取指定知识库的详细信息，包含文档列表。")
    @GetMapping("/knowledge-bases/{id}")
    public ResponseUtil getKnowledgeBase(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        Map<String, Object> result = ragService.getKnowledgeBase(email, id);
        return ResponseUtil.success(result);
    }

    @Operation(summary = "更新知识库", description = "更新知识库的名称和描述。")
    @PutMapping("/knowledge-bases/{id}")
    public ResponseUtil updateKnowledgeBase(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        String name = body.get("name");
        String description = body.get("description");
        ragService.updateKnowledgeBase(email, id, name, description);
        return ResponseUtil.success(null, "更新成功");
    }

    @Operation(summary = "删除知识库", description = "软删除知识库及其所有文档。删除后 30 天内可恢复，超期自动清理。")
    @DeleteMapping("/knowledge-bases/{id}")
    public ResponseUtil deleteKnowledgeBase(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        ragService.deleteKnowledgeBase(email, id);
        return ResponseUtil.success(null, "已删除");
    }

    // ==================== 文档管理 ====================

    @Operation(summary = "上传文档", description = "上传文档到指定知识库。支持 PDF、DOCX、DOC、TXT、MD 格式，最大 10MB。" +
            "上传成功后自动进入异步处理管道（解析→分块→嵌入→存储）。")
    @PostMapping("/knowledge-bases/{id}/documents")
    public ResponseUtil uploadDocument(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "文档文件", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        Map<String, Object> result = ragService.uploadDocument(email, id, file);
        return ResponseUtil.success("文档已上传，正在处理中", result);
    }

    @Operation(summary = "获取文档列表", description = "获取指定知识库中的所有文档，包含处理状态信息。")
    @GetMapping("/knowledge-bases/{id}/documents")
    public ResponseUtil listDocuments(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        List<Map<String, Object>> result = ragService.listDocuments(email, id);
        return ResponseUtil.success(result);
    }

    @Operation(summary = "删除文档", description = "软删除指定文档及其所有文本块。")
    @DeleteMapping("/knowledge-bases/{id}/documents/{docId}")
    public ResponseUtil deleteDocument(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long id,
            @Parameter(description = "文档 ID", required = true, example = "1")
            @PathVariable Long docId,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        ragService.deleteDocument(email, id, docId);
        return ResponseUtil.success(null, "已删除");
    }

    // ==================== 检索查询 ====================

    @Operation(summary = "检索知识库", description = "在指定知识库中检索与查询文本相关的文本块。" +
            "返回相关性分数、来源文件名和文本内容。用于测试检索效果。")
    @PostMapping("/knowledge-bases/{id}/query")
    public ResponseUtil queryKnowledgeBase(
            @Parameter(description = "知识库 ID", required = true, example = "1")
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        // 先验证知识库所有权
        ragService.getKnowledgeBase(email, id);
        // 执行检索
        String query = (String) body.getOrDefault("query", "");
        int maxResults = body.get("maxResults") != null
                ? ((Number) body.get("maxResults")).intValue() : 5;
        double minScore = body.get("minScore") != null
                ? ((Number) body.get("minScore")).doubleValue() : 0.3;
        List<Map<String, Object>> results = ragService.searchChunks(id, query, maxResults, minScore);
        return ResponseUtil.success(results);
    }
}
