package com.zyt.utils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.config.WechatConfig;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;

/**
 * 微信 OAuth 2.0 API 工具类
 *
 * 调用微信服务端接口完成 code → access_token → 用户信息 的交换流程
 * 使用 RestTemplate 发起 HTTP GET 请求，Jackson 反序列化 JSON 响应
 */
@Component
public class WechatUtil {

    private static final Logger log = LoggerFactory.getLogger(WechatUtil.class);

    @Resource
    private WechatConfig wechatConfig;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 用授权码换取 access_token 和 openid
     * GET https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=SECRET&code=CODE&grant_type=authorization_code
     */
    public WechatTokenResponse getAccessToken(String code) {
        String url = WechatConfig.OAUTH_ACCESS_TOKEN_URL
                + "?appid=" + wechatConfig.getAppId()
                + "&secret=" + wechatConfig.getAppSecret()
                + "&code=" + code
                + "&grant_type=authorization_code";
        log.info("微信 API 调用: 换取 access_token");
        String json = restTemplate.getForObject(url, String.class);
        try {
            WechatTokenResponse resp = objectMapper.readValue(json, WechatTokenResponse.class);
            if (Objects.nonNull(resp.getErrCode()) && resp.getErrCode() != 0) {
                log.error("微信 access_token 接口错误: errcode={}, errmsg={}", resp.getErrCode(), resp.getErrMsg());
                return null;
            }
            return resp;
        } catch (Exception e) {
            log.error("微信 access_token 响应解析失败: {}", json, e);
            return null;
        }
    }

    /**
     * 用 access_token 和 openid 获取微信用户信息
     * GET https://api.weixin.qq.com/sns/userinfo?access_token=ACCESS_TOKEN&openid=OPENID&lang=zh_CN
     */
    public WechatUserInfo getUserInfo(String accessToken, String openid) {
        String url = WechatConfig.OAUTH_USERINFO_URL
                + "?access_token=" + accessToken
                + "&openid=" + openid
                + "&lang=zh_CN";
        log.info("微信 API 调用: 获取用户信息 openid={}", openid);
        String json = restTemplate.getForObject(url, String.class);
        try {
            WechatUserInfo resp = objectMapper.readValue(json, WechatUserInfo.class);
            if (Objects.nonNull(resp.getErrCode()) && resp.getErrCode() != 0) {
                log.error("微信 userinfo 接口错误: errcode={}, errmsg={}", resp.getErrCode(), resp.getErrMsg());
                return null;
            }
            return resp;
        } catch (Exception e) {
            log.error("微信 userinfo 响应解析失败: {}", json, e);
            return null;
        }
    }

    // ==================== 微信 API 响应 DTO ====================

    /**
     * access_token 接口响应
     * 成功: { "access_token":"...", "expires_in":7200, "refresh_token":"...", "openid":"...", "scope":"..." }
     * 失败: { "errcode":40029, "errmsg":"invalid code" }
     */
    public static class WechatTokenResponse {
        @JsonProperty("access_token")
        private String accessToken;
        @JsonProperty("expires_in")
        private Integer expiresIn;
        @JsonProperty("refresh_token")
        private String refreshToken;
        private String openid;
        private String scope;
        @JsonProperty("unionid")
        private String unionId;
        private Integer errcode;
        private String errmsg;

        public String getAccessToken() { return accessToken; }
        public Integer getExpiresIn() { return expiresIn; }
        public String getRefreshToken() { return refreshToken; }
        public String getOpenid() { return openid; }
        public String getScope() { return scope; }
        public String getUnionId() { return unionId; }
        public Integer getErrCode() { return errcode; }
        public String getErrMsg() { return errmsg; }
    }

    /**
     * userinfo 接口响应
     * 成功: { "openid":"...", "nickname":"...", "sex":1, "headimgurl":"...", ... }
     * 失败: { "errcode":40003, "errmsg":"invalid openid" }
     */
    public static class WechatUserInfo {
        private String openid;
        private String nickname;
        private Integer sex;
        private String province;
        private String city;
        private String country;
        @JsonProperty("headimgurl")
        private String headImgUrl;
        @JsonProperty("unionid")
        private String unionId;
        private Integer errcode;
        private String errmsg;

        public String getOpenid() { return openid; }
        public String getNickname() { return nickname; }
        public Integer getSex() { return sex; }
        public String getProvince() { return province; }
        public String getCity() { return city; }
        public String getCountry() { return country; }
        public String getHeadImgUrl() { return headImgUrl; }
        public String getUnionId() { return unionId; }
        public Integer getErrCode() { return errcode; }
        public String getErrMsg() { return errmsg; }
    }
}
