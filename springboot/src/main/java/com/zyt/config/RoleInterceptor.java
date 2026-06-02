package com.zyt.config;

import com.zyt.utils.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

/**
 * 角色权限拦截器
 * 在 LoginInterceptor 之后执行，检查 @RequireRole 注解
 * 依赖 LoginInterceptor 设置的 request attributes（userEmail, userRole）
 *
 * 执行流程：
 *   1. 跳过非 HandlerMethod 的请求（静态资源等）
 *   2. 查找 @RequireRole 注解（方法级 > 类级）
 *   3. 无注解 → 放行（仅需登录即可）
 *   4. 有注解 → 比对 request.getAttribute("userRole")
 *   5. 匹配 → 放行；不匹配 → 403 Forbidden
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // 仅对 Controller 方法生效，跳过静态资源
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 方法级注解优先，其次类级
        RequireRole methodAnnotation = handlerMethod.getMethodAnnotation(RequireRole.class);
        RequireRole classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        RequireRole annotation = methodAnnotation != null ? methodAnnotation : classAnnotation;

        // 无角色要求 → 放行
        if (annotation == null) {
            return true;
        }

        // 读取 LoginInterceptor 设置的用户角色
        String userRole = (String) request.getAttribute("userRole");
        if (userRole == null) {
            writeError(response, "未登录，无法访问");
            return false;
        }

        if (!annotation.value().equals(userRole)) {
            writeError(response, "权限不足，需要 " + annotation.value() + " 权限");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(ResponseUtil.toJsonError(403, msg));
        writer.flush();
    }
}
