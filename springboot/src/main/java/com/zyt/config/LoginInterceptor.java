package com.zyt.config;

import com.zyt.utils.JwtUtil;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;
import java.util.Objects;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String TOKEN_PREFIX = "token:";

    /**
     * 登录拦截器核心逻辑 —— 三层校验链路
     *
     * 请求到达 → 1. 提取 Authorization 头中的 accessToken
     *           → 2. JWT 自校验（签名 + 过期时间）
     *           → 3. Redis 比对校验（单设备登录 / 登出检测）
     *           → 通过，放行到 Controller
     *
     * 注意：此拦截器仅校验 accessToken，refreshToken 在 /user/refresh
     * 端点的 Service 层独立校验（该端点已被 WebConfig 排除在拦截器之外）
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        // 第一层：Token 不存在或格式错误
        if (Objects.isNull(token) || token.trim().isEmpty()) {
            writeError(response, "未携带 Token，请先登录");
            return false;
        }
        // 第二层：JWT 自校验 —— 签名是否被篡改、是否过期
        if (!jwtUtil.validateToken(token)) {
            writeError(response, "Token 无效或已过期");
            return false;
        }
        // 第三层：Redis 比对 —— 是否已被新登录踢掉（单设备登录）或主动登出
        String username = jwtUtil.getUsernameFromToken(token);
        String redisToken = stringRedisTemplate.opsForValue().get(TOKEN_PREFIX + username);
        if (Objects.isNull(redisToken) || !redisToken.equals(token)) {
            writeError(response, "Token 已失效，请重新登录");
            return false;
        }
        // 鉴权通过，存储用户身份信息供下游 RoleInterceptor 和 Controller 使用
        request.setAttribute("userEmail", username);
        request.setAttribute("userRole", jwtUtil.getRoleFromToken(token));
        return true;
    }

    private void writeError(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write("{\"code\":401,\"msg\":\"" + msg + "\",\"data\":null}");
        writer.flush();
    }
}
