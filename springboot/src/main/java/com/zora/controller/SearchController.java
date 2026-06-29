package com.zora.controller;

import com.zora.service.SearchService;
import com.zora.utils.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 全文搜索控制器
 * <p>
 * Phase 4 全文搜索引擎 —— 提供消息内容的跨对话全文搜索 API。
 * 基于 MySQL FULLTEXT + ngram parser，支持中文分词、相关性排序和关键词高亮。
 * </p>
 */
@RestController
@RequestMapping("/search")
@Tag(name = "全文搜索", description = "基于 MySQL FULLTEXT 的消息内容跨对话全文搜索，支持中文分词和关键词高亮")
public class SearchController {

    @Resource
    private SearchService searchService;

    @Operation(
            summary = "全文搜索消息",
            description = "搜索当前用户所有对话中的消息内容。" +
                    "基于 MySQL FULLTEXT + ngram parser 实现中文分词，结果按相关性降序排列。" +
                    "关键词自动高亮（用 &lt;mark&gt; 标签包裹）。"
    )
    @com.zora.config.tracking.TrackAction("search_query")
    @GetMapping("/messages")
    public ResponseUtil searchMessages(
            @Parameter(description = "搜索关键词（必填，最多 200 字符）", required = true, example = "Spring Boot")
            @RequestParam String q,
            @Parameter(description = "页码（从 1 开始）", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量（默认 20，最大 50）", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(searchService.searchMessages(email, q, page, size));
    }
}
