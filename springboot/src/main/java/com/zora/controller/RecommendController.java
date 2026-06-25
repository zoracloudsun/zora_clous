package com.zora.controller;

import com.zora.service.RecommendService;
import com.zora.utils.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/**
 * 智能推荐控制器
 * <p>
 * Phase 4 智能推荐 —— 基于用户历史对话内容和行为数据，
 * 提供个性化推荐：相关对话、建议问题和热门知识库。
 * </p>
 */
@RestController
@RequestMapping("/recommend")
@Tag(name = "智能推荐", description = "基于用户历史对话和行为数据的个性化推荐：相关对话、建议问题、热门知识库")
public class RecommendController {

    @Resource
    private RecommendService recommendService;

    @Operation(
            summary = "获取推荐内容",
            description = "返回个性化推荐内容，包含三个维度：" +
                    "相关历史对话（基于内容相似度）、建议问题（基于主题匹配）、热门知识库（按文档数量排序）。"
    )
    @GetMapping("/suggestions")
    public ResponseUtil getRecommendations(
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        return ResponseUtil.success(recommendService.getRecommendations(email));
    }
}
