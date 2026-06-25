package com.zora.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zora.entity.User;
import com.zora.entity.UserActionLog;
import com.zora.mapper.UserActionLogMapper;
import com.zora.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 行为日志异步写入组件
 * <p>
 * Phase 4 用户行为分析 —— 将行为日志的数据库写入操作放在独立的异步方法中执行，
 * 避免 {@code @Async} 直接标注在 AOP 切面上导致 HttpServletRequest 被回收的问题。
 * </p>
 *
 * <h3>为什么要独立出一个组件？</h3>
 * <p>
 * {@code @Async} 在 Spring AOP 中通过代理实现。如果标注在切面类的私有方法上，
 * 或者通过 {@code this.asyncMethod()} 自调用，代理不会拦截。因此将异步方法
 * 放在独立的 {@code @Component} 中，保证跨 Bean 调用时 {@code @Async} 生效。
 * </p>
 *
 * <h3>与 ActionLogAspect 的分工</h3>
 * <ul>
 * <li>{@code ActionLogAspect}：主线程同步执行，从 HttpServletRequest 提取 email / IP / action</li>
 * <li>{@code ActionLogWriter}：异步线程执行，完成 userId 查找 + DB INSERT</li>
 * </ul>
 *
 * @see ActionLogAspect
 */
@Component
public class ActionLogWriter {

    private static final Logger log = LoggerFactory.getLogger(ActionLogWriter.class);

    @Resource
    private UserActionLogMapper actionLogMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 异步写入行为日志
     * <p>
     * 接收的是已经从 HttpServletRequest 中提取出来的纯字符串参数，
     * 不依赖任何请求作用域对象，可在任意线程中安全执行。
     * </p>
     *
     * @param email  用户邮箱（从 request.getAttribute("userEmail") 提取）
     * @param action 行为类型（conv_create / message_send / search_query / kb_upload / kb_query / agent_call）
     * @param ip     客户端 IP（从 request 提取，已在主线程完成代理头解析）
     */
    @Async
    public void write(String email, String action, String ip) {
        try {
            // 1. 查找用户 ID
            Integer userId = resolveUserId(email);
            if (userId == null) {
                return;
            }

            // 2. 构建并保存行为日志
            UserActionLog actionLog = new UserActionLog();
            actionLog.setUserId(userId);
            actionLog.setAction(action);
            actionLog.setIpAddress(ip);
            actionLogMapper.insert(actionLog);

            log.debug("行为日志已写入: userId={}, action={}, ip={}", userId, action, ip);
        } catch (Exception e) {
            // 写入失败仅 warn，不抛出异常（行为日志不应中断主流程）
            log.warn("行为日志写入失败: action={}, error={}", action, e.getMessage());
        }
    }

    /**
     * 根据邮箱获取用户 ID
     */
    private Integer resolveUserId(String email) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getEmail, email);
        User user = userMapper.selectOne(wrapper);
        if (user == null) {
            log.debug("用户不存在，跳过日志: {}", email);
            return null;
        }
        return user.getId();
    }
}
