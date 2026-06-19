package com.zora.controller;

import com.zora.agent.AgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Agent 智能体控制器（Phase 3）
 * <p>
 * 提供基于 LangChain4j AiServices 的 Agent SSE 流式对话端点。
 * 与 {@code AiChatController} 的区别：
 * <ul>
 * <li>支持工具调用（Tool Calling）—— AI 可主动调用搜索、计算等工具</li>
 * <li>结构化 SSE 事件协议——传输思考过程和工具调用状态</li>
 * <li>两阶段流式架构——先非流式推理，再流式输出最终回答</li>
 * </ul>
 * </p>
 *
 * <h3>SSE 事件类型</h3>
 * <table>
 * <tr><td>thinking</td><td>Agent 思考过程</td></tr>
 * <tr><td>tool_call</td><td>工具调用请求</td></tr>
 * <tr><td>tool_result</td><td>工具执行结果</td></tr>
 * <tr><td>token</td><td>最终回答文本 token</td></tr>
 * <tr><td>done</td><td>对话结束</td></tr>
 * <tr><td>error</td><td>错误信息</td></tr>
 * </table>
 *
 * @see AgentService
 * @see com.zora.agent.event.AgentEvent
 */
@RestController
@RequestMapping("/agent")
@Tag(name = "AI Agent 智能体", description = "基于 LangChain4j Tool Calling 的 AI Agent 系统：流式对话、工具调用、推理可视化")
public class AgentController {

    /** 用户消息最大长度（约 2000 中文字） */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    @Resource
    private AgentService agentService;

    /**
     * Agent SSE 流式对话
     * <p>
     * 接收用户消息，执行 Agent 推理循环（工具调用），
     * 以 SSE (text/event-stream) 流式返回结构化事件和最终回答。
     * </p>
     *
     * <h3>请求体</h3>
     * <pre>{@code
     * {
     *   "message": "帮我搜索最新的 AI 新闻，然后总结要点",
     *   "conversationId": 1   // 可选，不传则自动创建新对话
     * }
     * }</pre>
     *
     * <h3>SSE 事件示例</h3>
     * <pre>{@code
     * data: {"type":"thinking","content":"正在分析您的问题..."}
     * data: {"type":"tool_call","tool":"searchWeb","args":{"query":"AI 新闻 2025"}}
     * data: {"type":"tool_result","tool":"searchWeb","content":"[搜索结果...]"}
     * data: {"type":"token","content":"根据搜索结果..."}
     * data: {"type":"done","conversationId":42}
     * }</pre>
     *
     * @param body    请求体（message + 可选 conversationId）
     * @param request HTTP 请求（由 LoginInterceptor 注入 userEmail）
     * @return SSE 流式事件序列
     */
    @Operation(
            summary = "Agent 流式对话",
            description = "发送消息给 AI Agent，Agent 可自主调用工具（搜索、计算、代码执行）来回答问题。" +
                    "返回 SSE (text/event-stream) 流，事件类型包括 thinking（思考）、tool_call（工具调用）、" +
                    "tool_result（工具结果）、token（回答片段）、done（完成）。" +
                    "消息长度限制 4000 字符。支持传入 conversationId 继续已有对话。")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> agentStreamChat(
            @RequestBody Map<String, Object> body,
            @Parameter(description = "当前登录用户（由 LoginInterceptor 自动注入）", hidden = true)
            HttpServletRequest request) {

        String email = (String) request.getAttribute("userEmail");
        String message = (String) body.get("message");
        Long conversationId = body.get("conversationId") != null
                ? Long.valueOf(body.get("conversationId").toString())
                : null;

        // 消息非空 + 长度校验
        if (message == null || message.isBlank()) {
            return Flux.error(new IllegalArgumentException("消息不能为空"));
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            return Flux.error(new IllegalArgumentException(
                    "消息长度不能超过 " + MAX_MESSAGE_LENGTH + " 个字符"));
        }

        return agentService.agentStreamChat(email, message, conversationId);
    }
}
