package com.zora.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 3 配置
 * 启动后访问 http://localhost:8080/doc.html
 *
 * 全局 Authorization 配置：
 * 在 Knife4j UI 右上角填入 accessToken 后，所有接口自动携带 Authorization header，
 * 在线调试不需要每个接口单独填写 Token。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Spring Boot Auth 用户认证系统")
                        .version("1.0")
                        .description("""
                                前后端分离的用户认证系统 API 文档

                                ## 功能模块
                                - **基础认证**：图形验证码、邮箱验证码注册、密码登录、JWT 双 Token、登出
                                - **微信扫码登录**：真实 OAuth 2.0、已绑定直接登录、新用户邮箱绑定
                                - **邮箱找回密码**：验证码重置密码、踢掉所有设备
                                - **RBAC 角色权限**：user/admin 双角色、管理员用户管理
                                - **AI 对话**：基于 DeepSeek 的 SSE 流式对话、会话管理、回收站
                                - **RAG 知识库**：文档上传、向量检索、知识库增强对话
                                - **AI Agent 智能体**：Tool Calling 工具调用、多轮推理、多 Agent 协作

                                ## 鉴权说明
                                登录成功后获取 accessToken，在右上角 **Authorize** 按钮填入即可调试所有接口。
                                Token 格式：直接填入 JWT 字符串（不带 Bearer 前缀）。
                                """))
                // 全局 Authorization header — 在线调试时自动注入
                .addSecurityItem(new SecurityRequirement().addList("Authorization"))
                .components(new Components()
                        .addSecuritySchemes("Authorization", new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("JWT accessToken（登录接口返回，直接填入即可，无需 Bearer 前缀）")));
    }

    /**
     * 全局安全自定义器：将 Authorization 安全要求注入到每个 Operation
     *
     * 背景：Knife4j 不会自动继承 OpenAPI 根级别的 security 声明。
     * 必须在每个 Operation 的 security 字段中显式声明，Authorize 按钮填入的 Token
     * 才会被注入到该 Operation 的请求中。
     *
     * SpringDoc 在生成完所有 Operation 之后调用此 Customizer，
     * 我们在此时为所有 Operation 统一添加 SecurityRequirement。
     */
    @Bean
    public GlobalOpenApiCustomizer globalSecurityCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null)
                return;
            openApi.getPaths().forEach((path, pathItem) -> {
                // GET 操作
                if (pathItem.getGet() != null) {
                    pathItem.getGet().addSecurityItem(
                            new SecurityRequirement().addList("Authorization"));
                }
                // POST 操作
                if (pathItem.getPost() != null) {
                    pathItem.getPost().addSecurityItem(
                            new SecurityRequirement().addList("Authorization"));
                }
                // PUT 操作（预留）
                if (pathItem.getPut() != null) {
                    pathItem.getPut().addSecurityItem(
                            new SecurityRequirement().addList("Authorization"));
                }
                // DELETE 操作（预留）
                if (pathItem.getDelete() != null) {
                    pathItem.getDelete().addSecurityItem(
                            new SecurityRequirement().addList("Authorization"));
                }
            });
        };
    }
}
