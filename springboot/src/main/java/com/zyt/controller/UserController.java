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

    /** 获取图形验证码（仅登录使用） */
    @GetMapping("/captcha")
    public ResponseUtil captcha() {
        Map<String, String> data = userService.generateCaptcha();
        return new ResponseUtil(200, "验证码生成成功", data);
    }

    /** 发送邮箱验证码（注册用，已注册邮箱不发送） */
    @PostMapping("/send-code")
    public ResponseUtil sendCode(@RequestBody Map<String, String> body) {
        return userService.sendCode(body.get("email"));
    }

    /** 邮箱验证码注册 */
    @PostMapping("/register")
    public ResponseUtil register(@RequestBody Map<String, String> body) {
        return userService.register(body.get("email"), body.get("password"), body.get("code"));
    }

    /** 登录（需图形验证码） */
    @PostMapping("/login")
    public ResponseUtil login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("email"), body.get("password"),
                body.get("captchaId"), body.get("captchaCode"));
    }

    @PostMapping("/logout")
    public ResponseUtil logout(@RequestHeader("Authorization") String token) {
        return userService.logout(token);
    }

    @PostMapping("/refresh")
    public ResponseUtil refresh(@RequestBody Map<String, String> body) {
        return userService.refresh(body.get("refreshToken"));
    }

    @GetMapping("/info")
    public ResponseUtil info() {
        return new ResponseUtil(200, "鉴权通过，可正常访问", null);
    }
}
