package com.zyt.controller;

import com.zyt.config.RequireRole;
import com.zyt.service.UserService;
import com.zyt.utils.ResponseUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/user")
@Tag(name = "用户认证", description = "图形验证码、邮箱验证码注册、密码登录、JWT 双 Token、登出、Token 刷新、微信 OAuth 扫码、邮箱找回密码、RBAC 权限管理")
public class UserController {

    @Resource
    private UserService userService;

    // ==================== 基础认证 ====================

    @Operation(summary = "获取图形验证码", description = "登录前获取 6 位大写字母验证码图片（Base64），Redis 1 分钟有效，一次性使用")
    @GetMapping("/captcha")
    public ResponseUtil captcha() {
        Map<String, String> data = userService.generateCaptcha();
        return new ResponseUtil(200, "验证码生成成功", data);
    }

    @Operation(summary = "发送邮箱验证码（注册用）", description = "向未注册邮箱发送 6 位数字验证码，Redis 5 分钟有效，60 秒防刷。已注册邮箱提示直接登录")
    @PostMapping("/send-code")
    public ResponseUtil sendCode(@RequestBody Map<String, String> body) {
        return userService.sendCode(body.get("email"));
    }

    @Operation(summary = "邮箱验证码注册", description = "使用邮箱验证码完成注册，密码 BCrypt 加密存储，默认角色 user")
    @PostMapping("/register")
    public ResponseUtil register(@RequestBody Map<String, String> body) {
        return userService.register(body.get("email"), body.get("password"), body.get("code"));
    }

    @Operation(summary = "密码登录", description = "验证图形验证码 + 邮箱 + 密码，成功返回双 Token + 角色。5 次失败锁定 15 分钟，统一错误提示防用户枚举")
    @PostMapping("/login")
    public ResponseUtil login(@RequestBody Map<String, String> body) {
        return userService.login(body.get("email"), body.get("password"),
                body.get("captchaId"), body.get("captchaCode"));
    }

    @Operation(summary = "登出", description = "清除 Redis 中的 accessToken 和 refreshToken，当前设备下线")
    @PostMapping("/logout")
    public ResponseUtil logout(
            @Parameter(description = "JWT accessToken", required = true)
            @RequestHeader("Authorization") String token) {
        return userService.logout(token);
    }

    @Operation(summary = "刷新 accessToken", description = "使用 refreshToken 换取新的 accessToken（30min），refreshToken 7 天有效。强制校验 type=refresh 防 accessToken 无限续期")
    @PostMapping("/refresh")
    public ResponseUtil refresh(@RequestBody Map<String, String> body) {
        return userService.refresh(body.get("refreshToken"));
    }

    @Operation(summary = "鉴权探针", description = "验证当前 accessToken 是否有效，用于前端判断登录状态")
    @GetMapping("/info")
    public ResponseUtil info() {
        return new ResponseUtil(200, "鉴权通过，可正常访问", null);
    }

    // ==================== 微信扫码登录 ====================

    @Operation(summary = "生成微信扫码场景 [微信]", description = "返回 sceneId + 真实微信 OAuth 授权 URL，前端生成二维码。sceneId 用于轮询扫码状态（5 分钟有效）")
    @PostMapping("/wechat/qrcode")
    public ResponseUtil wechatQrcode() {
        return userService.wechatQrcode();
    }

    @Operation(summary = "轮询扫码状态 [微信]", description = "PC 端每 2 秒轮询：pending（等待扫码）/ scanned（已扫码，需绑定邮箱）/ confirmed（已登录，返回 Token）/ expired（过期）")
    @GetMapping("/wechat/check")
    public ResponseUtil wechatCheck(
            @Parameter(description = "场景 ID", required = true, example = "uuid-string")
            @RequestParam String sceneId) {
        return userService.wechatCheck(sceneId);
    }

    @Operation(summary = "微信 OAuth 回调（手机浏览器访问，已隐藏）", description = "微信在用户确认授权后重定向到此 URL（手机浏览器访问），返回 HTML 页面而非 JSON",
            hidden = true)
    @GetMapping(value = "/wechat/callback", produces = "text/html;charset=UTF-8")
    public String wechatCallback(@RequestParam(required = false) String code,
                                 @RequestParam(required = false) String state) {
        ResponseUtil result = userService.wechatCallback(code, state);
        if (result.getCode() == 200) {
            return "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>授权成功</title><style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#f5f5f5;margin:0}.card{background:#fff;padding:40px;border-radius:12px;text-align:center;box-shadow:0 2px 20px rgba(0,0,0,0.1)}h2{color:#31c27c;margin-bottom:8px}p{color:#666;font-size:14px}</style></head><body><div class='card'><h2>✅ 授权成功</h2><p>请返回电脑继续完成邮箱绑定</p></div></body></html>";
        }
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>授权失败</title><style>body{font-family:sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#f5f5f5;margin:0}.card{background:#fff;padding:40px;border-radius:12px;text-align:center;box-shadow:0 2px 20px rgba(0,0,0,0.1)}h2{color:#f56c6c;margin-bottom:8px}p{color:#666;font-size:14px}</style></head><body><div class='card'><h2>❌ 授权失败</h2><p>" + result.getMsg() + "</p></div></body></html>";
    }

    @Operation(summary = "微信扫码后绑定邮箱 [微信]", description = "新用户扫码后提交邮箱 + 验证码，创建账号并绑定微信 openid / 已注册用户输入邮箱验证码后将微信关联到已有账号")
    @PostMapping("/wechat/bind-email")
    public ResponseUtil bindWechatEmail(@RequestBody Map<String, String> body) {
        return userService.bindWechatEmail(
                body.get("sceneId"), body.get("email"), body.get("code"));
    }

    @Operation(summary = "发送绑定邮箱验证码 [微信]", description = "微信扫码绑定用，与注册验证码的区别：允许向已注册邮箱发送（用于将微信绑定到已有账号）")
    @PostMapping("/send-bind-code")
    public ResponseUtil sendBindCode(@RequestBody Map<String, String> body) {
        return userService.sendBindCode(body.get("email"));
    }

    // ==================== 邮箱找回密码 ====================

    @Operation(summary = "发送密码重置验证码 [找回密码]", description = "仅向已注册邮箱发送 6 位数字验证码，Redis 5 分钟有效，60 秒防刷")
    @PostMapping("/forgot-password/send-code")
    public ResponseUtil sendResetCode(@RequestBody Map<String, String> body) {
        return userService.sendResetCode(body.get("email"));
    }

    @Operation(summary = "重置密码 [找回密码]", description = "校验验证码 → BCrypt 加密新密码 → 更新 DB → 清除所有 Token → 所有设备重新登录")
    @PostMapping("/forgot-password/reset")
    public ResponseUtil resetPassword(@RequestBody Map<String, String> body) {
        return userService.resetPassword(body.get("email"), body.get("password"), body.get("code"));
    }

    // ==================== RBAC 角色权限 ====================

    @Operation(summary = "分页查询所有用户 [RBAC·管理员]", description = "需 admin 角色，返回分页用户列表（不含密码字段）")
    @RequireRole("admin")
    @GetMapping("/admin/users")
    public ResponseUtil listUsers(
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return new ResponseUtil(200, "查询成功", userService.listUsers(page, size));
    }

    @Operation(summary = "获取当前用户信息 [RBAC]", description = "根据 accessToken 返回当前用户的 id、email、role、nickname、avatar")
    @GetMapping("/me")
    public ResponseUtil me(HttpServletRequest request) {
        String email = (String) request.getAttribute("userEmail");
        if (email == null) {
            return new ResponseUtil(401, "未登录", null);
        }
        Map<String, Object> info = userService.getCurrentUser(email);
        if (info == null) {
            return new ResponseUtil(404, "用户不存在", null);
        }
        return new ResponseUtil(200, "查询成功", info);
    }
}
