package com.zora.agent;

import reactor.core.publisher.Flux;

/**
 * Agent 智能体服务接口（Phase 3）
 * <p>
 * 定义 Agent 流式对话的核心契约。
 * Agent 与普通 AI 对话的主要区别：
 * <ul>
 * <li>支持工具调用（Tool Calling）—— AI 可以主动调用搜索、计算等工具</li>
 * <li>两阶段流式架构——先用非流式模型完成推理循环，再用流式模型输出最终回答</li>
 * <li>结构化 SSE 事件——通过 {@code AgentEvent} 传输思考过程和工具调用状态</li>
 * </ul>
 * </p>
 *
 * @see com.zora.agent.impl.AgentServiceImpl
 * @see com.zora.agent.event.AgentEvent
 */
public interface AgentService {

    /**
     * Agent 流式对话（核心方法）
     * <p>
     * 处理流程：
     * <ol>
     * <li>限流检查（Redis ZSET 滑动窗口，10次/分钟/用户）</li>
     * <li>Prompt 注入检测（18 种攻击模式）</li>
     * <li>用户查找 + 对话管理</li>
     * <li>Agent 推理循环（非流式 → 工具调用 → 结果分析 → 最终答案）</li>
     * <li>SSE 流式输出最终回答</li>
     * </ol>
     * </p>
     *
     * @param email          当前用户邮箱（从 JWT Token 解析）
     * @param userMessage    用户输入的消息文本
     * @param conversationId 会话 ID（为 null 时自动创建新对话）
     * @return SSE 流式事件序列（每个元素是一个 JSON 编码的 AgentEvent）
     */
    Flux<String> agentStreamChat(String email, String userMessage, Long conversationId);
}
