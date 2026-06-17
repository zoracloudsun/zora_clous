package com.zora.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信开放平台 / 测试号配置
 *
 * 获取测试号凭证（免费，无需企业资质）：
 * 1. 浏览器打开 https://mp.weixin.qq.com/debug/cgi-bin/sandbox?t=sandbox/login
 * 2. 用微信扫码登录 → 获得 appId 和 appSecret
 * 3. 在页面下方「OAuth2.0网页授权」处填写回调域名（ngrok 域名，不含 http(s)://）
 * 4. 将 appId、appSecret 填入 application.yml
 *
 * 内网穿透（本地开发必备）：
 * ngrok http 8080
 * → 获得 https://xxxx.ngrok-free.app
 * → callback-url: https://xxxx.ngrok-free.app/user/wechat/callback
 */
@Component
public class WechatConfig {

    @Value("${wechat.app-id:}")
    private String appId;

    @Value("${wechat.app-secret:}")
    private String appSecret;

    /** 微信 OAuth 回调地址：需配置在测试号「OAuth2.0网页授权回调域名」中 */
    @Value("${wechat.callback-url:http://localhost:8080/user/wechat/callback}")
    private String callbackUrl;

    // ==================== 微信 API 端点 ====================
    // 网页授权（snsapi_userinfo：获取用户昵称头像，需用户手动授权）
    // 注意：测试号必须使用 /oauth2/authorize，不能使用 /qrconnect（后者仅开放平台可用）
    public static final String OAUTH_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";
    public static final String OAUTH_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    public static final String OAUTH_USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    public String getAppId() {
        return appId;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public String getCallbackUrl() {
        return callbackUrl;
    }

    /** 检查是否已配置微信凭证（未配置则无法使用真实扫码） */
    public boolean isConfigured() {
        return appId != null && !appId.isBlank()
                && appSecret != null && !appSecret.isBlank();
    }
}
