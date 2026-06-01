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

    // ==================== 微信扫码登录 ====================

    /** 生成微信扫码场景，返回 sceneId + 微信 OAuth 授权 URL */
    @PostMapping("/wechat/qrcode")
    public ResponseUtil wechatQrcode() {
        return userService.wechatQrcode();
    }

    /** 轮询扫码状态：pending / scanned（需绑定邮箱） / confirmed（已登录） / expired */
    @GetMapping("/wechat/check")
    public ResponseUtil wechatCheck(@RequestParam String sceneId) {
        return userService.wechatCheck(sceneId);
    }

    /**
     * 微信 OAuth 回调（手机浏览器访问，返回 HTML 页面）
     * 微信在用户确认授权后重定向到此 URL，附带 code 和 state 参数
     */
    @GetMapping(value = "/wechat/callback", produces = "text/html;charset=UTF-8")
    public String wechatCallback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state) {
        ResponseUtil result = userService.wechatCallback(code, state);
        if (result.getCode() == 200) {
            return "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>授权成功</title><style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#f5f5f5;margin:0}.card{background:#fff;padding:40px;border-radius:12px;text-align:center;box-shadow:0 2px 20px rgba(0,0,0,0.1)}h2{color:#31c27c;margin-bottom:8px}p{color:#666;font-size:14px}</style></head><body><div class='card'><h2>✅ 授权成功</h2><p>请返回电脑继续完成邮箱绑定</p></div></body></html>";
        }
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>授权失败</title><style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#f5f5f5;margin:0}.card{background:#fff;padding:40px;border-radius:12px;text-align:center;box-shadow:0 2px 20px rgba(0,0,0,0.1)}h2{color:#f56c6c;margin-bottom:8px}p{color:#666;font-size:14px}</style></head><body><div class='card'><h2>❌ 授权失败</h2><p>" + result.getMsg() + "</p></div></body></html>";
    }

    /** 微信扫码后绑定邮箱 */
    @PostMapping("/wechat/bind-email")
    public ResponseUtil bindWechatEmail(@RequestBody Map<String, String> body) {
        return userService.bindWechatEmail(
                body.get("sceneId"), body.get("email"), body.get("code"));
    }

    /** 微信绑定邮箱专用：发送邮箱验证码（允许已注册邮箱） */
    @PostMapping("/send-bind-code")
    public ResponseUtil sendBindCode(@RequestBody Map<String, String> body) {
        return userService.sendBindCode(body.get("email"));
    }

    // ==================== 邮箱找回密码 ====================

    /** 发送密码重置验证码（仅已注册邮箱发送） */
    @PostMapping("/forgot-password/send-code")
    public ResponseUtil sendResetCode(@RequestBody Map<String, String> body) {
        return userService.sendResetCode(body.get("email"));
    }

    /** 验证码校验 + 密码重置 + 踢掉所有设备 */
    @PostMapping("/forgot-password/reset")
    public ResponseUtil resetPassword(@RequestBody Map<String, String> body) {
        return userService.resetPassword(body.get("email"), body.get("password"), body.get("code"));
    }
}
