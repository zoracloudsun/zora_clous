package com.zora.config;

import java.lang.annotation.*;

/**
 * 用户行为追踪注解
 * <p>
 * Phase 4 用户行为分析 —— 标记 Controller 方法以自动记录用户操作日志。
 * 配合 {@link ActionLogAspect} AOP 切面使用，在方法成功返回后异步写入行为日志。
 * </p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @TrackAction("message_send")
 * public Flux<String> streamChat(...) { ... }
 *
 * @TrackAction("search_query")
 * public ResponseUtil searchMessages(...) { ... }
 * }</pre>
 *
 * <h3>支持的行为类型</h3>
 * <ul>
 * <li>{@code conv_create} — 创建对话</li>
 * <li>{@code message_send} — 发送消息</li>
 * <li>{@code search_query} — 搜索查询</li>
 * <li>{@code kb_upload} — 知识库上传文档</li>
 * <li>{@code kb_query} — 知识库检索</li>
 * <li>{@code agent_call} — Agent 调用</li>
 * </ul>
 *
 * @see ActionLogAspect
 * @see com.zora.entity.UserActionLog
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TrackAction {

    /**
     * 行为类型
     * 建议使用 {@link com.zora.entity.UserActionLog} 中定义的 ACTION_* 常量值
     */
    String value();
}
