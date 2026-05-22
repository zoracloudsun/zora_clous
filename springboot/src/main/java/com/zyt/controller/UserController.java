package com.zyt.controller;

import com.zyt.service.UserService;
import com.zyt.utils.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册 —— 无需 Token，开放访问
     */
    @PostMapping("/register")
    public ResponseUtil register(@RequestBody Map<String, String> body) {
        return userService.register(body.get("username"), body.get("password"));
    }

    /**
     * 用户登录 —— 无需 Token，返回双 Token（accessToken + refreshToken）
     */
    @PostMapping("/login")
    public ResponseUtil login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("username"), body.get("password"));
    }

    /**
     * 用户登出 —— 需要 accessToken，清除 Redis 中两个 Token
     */
    @PostMapping("/logout")
    public ResponseUtil logout(@RequestHeader("Authorization") String token) {
        return userService.logout(token);
    }

    /**
     * 刷新 accessToken —— 无需 accessToken，用 refreshToken 换取新的 accessToken
     * 此接口被拦截器放行，在 Service 层直接校验 refreshToken
     * 前端 Axios 在收到 401 时自动调用此接口，用户无感知
     */
    @PostMapping("/refresh")
    public ResponseUtil refresh(@RequestBody Map<String, String> body) {
        return userService.refresh(body.get("refreshToken"));
    }

    /**
     * 鉴权探针接口 —— 需要有效的 accessToken 才能访问
     * 能走到这里说明 LoginInterceptor 校验已通过
     */
    @GetMapping("/info")
    public ResponseUtil info() {
        return new ResponseUtil(200, "鉴权通过，可正常访问", null);
    }
}
