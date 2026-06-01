package com.zyt.service;

import com.zyt.utils.ResponseUtil;

import java.util.Map;

public interface UserService {
    Map<String, String> generateCaptcha();
    ResponseUtil sendCode(String email);
    ResponseUtil register(String email, String password, String code);
    ResponseUtil login(String email, String password, String captchaId, String captchaCode);
    ResponseUtil logout(String token);
    ResponseUtil refresh(String refreshToken);

    // ==================== 微信扫码登录 ====================
    /** 微信绑定邮箱专用：发送验证码（允许已注册邮箱） */
    ResponseUtil sendBindCode(String email);

    // ==================== 邮箱找回密码 ====================
    /** 发送密码重置验证码（仅已注册邮箱可发送） */
    ResponseUtil sendResetCode(String email);
    /** 验证码校验 + 密码重置 + 踢掉所有设备 */
    ResponseUtil resetPassword(String email, String newPassword, String code);

    ResponseUtil wechatQrcode();
    ResponseUtil wechatCheck(String sceneId);
    /** 微信 OAuth 回调：用 code 换取用户信息，存入 Redis */
    ResponseUtil wechatCallback(String code, String state);
    /** 扫码后绑定邮箱：验证码校验 + 创建/关联用户 + 签发 Token */
    ResponseUtil bindWechatEmail(String sceneId, String email, String code);

    // ==================== RBAC 角色权限 ====================
    /** 管理员分页查询所有用户（不含密码） */
    Map<String, Object> listUsers(int page, int size);
    /** 获取当前登录用户信息 */
    Map<String, Object> getCurrentUser(String email);
}
