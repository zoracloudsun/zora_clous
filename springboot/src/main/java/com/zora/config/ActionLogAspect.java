package com.zora.config;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 用户行为日志 AOP 切面
 * <p>
 * Phase 4 用户行为分析 —— 拦截带有 {@link TrackAction} 注解的方法，
 * 在方法成功返回后将行为日志委托给 {@link ActionLogWriter} 异步写入。
 * </p>
 *
 * <h3>设计要点</h3>
 * <ul>
 * <li><b>主线程提取数据</b>：在 {@code @AfterReturning} 切面中从 HttpServletRequest 同步提取
 *     email / action / IP，确保这些数据在 Request 被回收前获取完毕。</li>
 * <li><b>委托异步写入</b>：将提取出的纯字符串参数传给 {@link ActionLogWriter#write(String, String, String)}，
 *     该方法标注 {@code @Async}，在独立的线程池中执行 DB 写入。</li>
 * <li><b>为什么不在切面上直接用 @Async？</b>
 *     {@code HttpServletRequest} 是请求作用域对象，HTTP 响应发送后 Tomcat 会回收它。
 *     如果 {@code @Async} 标注在切面方法上，提取数据的代码也在异步线程中执行，
 *     此时 Request 可能已被回收，导致 "request object has been recycled" 错误。</li>
 * <li>{@code @AfterReturning} 仅在方法成功执行后才记录（异常不记录）</li>
 * </ul>
 *
 * @see ActionLogWriter
 */
@Aspect
@Component
public class ActionLogAspect {

    private static final Logger log = LoggerFactory.getLogger(ActionLogAspect.class);

    @Resource
    private ActionLogWriter actionLogWriter;

    /**
     * 拦截 @TrackAction 注解的方法，记录用户操作
     * <p>
     * ⚠️ 此方法运行在主线程（HTTP 请求线程），必须在 Request 被 Tomcat 回收前
     * 完成所有数据提取。耗时的 DB 写入操作委托给 {@link ActionLogWriter#write} 异步执行。
     * </p>
     *
     * @param joinPoint   切点（被拦截的方法）
     * @param trackAction 注解实例（包含行为类型）
     * @param result      方法返回值（暂未使用）
     */
    @AfterReturning(pointcut = "@annotation(trackAction)", returning = "result")
    public void logAction(JoinPoint joinPoint, TrackAction trackAction, Object result) {
        try {
            // 在主线程中提取所有需要的数据（Request 此时仍然有效）
            HttpServletRequest request = findRequest(joinPoint);
            if (request == null) {
                log.debug("未找到 HttpServletRequest，跳过行为日志记录");
                return;
            }

            String email = (String) request.getAttribute("userEmail");
            if (email == null || email.isBlank()) {
                log.debug("未登录用户操作，跳过行为日志记录");
                return;
            }

            String action = trackAction.value();
            String ip = getClientIp(request);

            // 委托异步写入（跨 Bean 调用，@Async 生效）
            actionLogWriter.write(email, action, ip);
        } catch (Exception e) {
            // 数据提取阶段的异常也不应中断主流程
            log.warn("行为日志数据提取失败: action={}, error={}", trackAction.value(), e.getMessage());
        }
    }

    /**
     * 从方法参数中查找 HttpServletRequest
     */
    private HttpServletRequest findRequest(JoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof HttpServletRequest) {
                return (HttpServletRequest) arg;
            }
        }
        return null;
    }

    /**
     * 获取客户端真实 IP
     * <p>
     * 优先从 X-Forwarded-For 头获取（经过代理时），
     * 其次取 RemoteAddr（直连时）。
     * </p>
     */
    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 多级代理时取第一个 IP
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
