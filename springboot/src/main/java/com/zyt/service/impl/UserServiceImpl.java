package com.zyt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.config.WechatConfig;
import com.zyt.entity.User;
import com.zyt.mapper.UserMapper;
import com.zyt.service.UserService;
import com.zyt.utils.CaptchaUtil;
import com.zyt.utils.EmailUtil;
import com.zyt.utils.JwtUtil;
import com.zyt.utils.ResponseUtil;
import com.zyt.utils.WechatUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private EmailUtil emailUtil;

    @Resource
    private WechatConfig wechatConfig;

    @Resource
    private WechatUtil wechatUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Redis key 前缀
    // token: —— accessToken 存储，30min TTL
    // refresh_token: —— refreshToken 存储，7天 TTL
    // email_code: —— 邮箱验证码存储，5min TTL
    // captcha: —— 图形验证码存储（仅登录使用），1min TTL
    // login_fail: —— 登录失败次数，15min TTL，超过5次锁定
    private static final String TOKEN_PREFIX = "token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String EMAIL_CODE_PREFIX = "email_code:";
    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final String LOGIN_FAIL_PREFIX = "login_fail:";
    private static final String WECHAT_SCENE_PREFIX = "wechat_scene:";
    private static final String WECHAT_DATA_PREFIX = "wechat_data:";
    private static final String WECHAT_TOKEN_PREFIX = "wechat_token:";

    private static final int MAX_LOGIN_FAIL_COUNT = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;
    private static final int WECHAT_SCENE_TTL_MINUTES = 5;

    /**
     * 生成图形验证码（仅登录使用）
     */
    @Override
    public Map<String, String> generateCaptcha() {
        CaptchaUtil.CaptchaResult result = CaptchaUtil.generate();
        String captchaId = UUID.randomUUID().toString();
        stringRedisTemplate.opsForValue().set(CAPTCHA_PREFIX + captchaId, result.getCode(), 1, TimeUnit.MINUTES);
        Map<String, String> data = new HashMap<>();
        data.put("captchaId", captchaId);
        data.put("captchaImage", result.getBase64Image());
        return data;
    }

    /**
     * 图形验证码校验（仅登录使用）
     */
    private ResponseUtil validateCaptcha(String captchaId, String captchaCode) {
        if (Objects.isNull(captchaId) || captchaId.trim().isEmpty()
                || Objects.isNull(captchaCode) || captchaCode.trim().isEmpty()) {
            return new ResponseUtil(400, "图形验证码不能为空", null);
        }
        String key = CAPTCHA_PREFIX + captchaId;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        if (Objects.isNull(storedCode)) {
            return new ResponseUtil(400, "图形验证码已过期，请刷新重试", null);
        }
        if (!storedCode.equalsIgnoreCase(captchaCode.trim())) {
            stringRedisTemplate.delete(key);
            return new ResponseUtil(400, "图形验证码错误", null);
        }
        stringRedisTemplate.delete(key);
        return null;
    }

    /**
     * 发送邮箱验证码
     * - 已注册邮箱不发送，提示直接登录
     * - 60秒内同一邮箱不可重复发送
     */
    @Override
    public ResponseUtil sendCode(String email) {
        if (Objects.isNull(email) || email.trim().isEmpty()) {
            return new ResponseUtil(400, "邮箱不能为空", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return new ResponseUtil(400, "邮箱格式不正确", null);
        }
        // 已注册邮箱不再发送验证码，提示直接登录
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (Objects.nonNull(exist)) {
            return new ResponseUtil(400, "该邮箱已注册，请直接登录", null);
        }
        // 60秒防刷
        String codeKey = EMAIL_CODE_PREFIX + email;
        String existingCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (Objects.nonNull(existingCode)) {
            Long ttl = stringRedisTemplate.getExpire(codeKey);
            if (Objects.nonNull(ttl) && ttl > 240) {
                return new ResponseUtil(400, "验证码已发送，" + (ttl - 240) + " 秒后可重新获取", null);
            }
        }
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        stringRedisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        emailUtil.sendVerificationCode(email, code);
        return new ResponseUtil(200, "验证码已发送至邮箱，5分钟内有效", null);
    }

    /**
     * 发送邮箱验证码（微信绑定邮箱专用）
     * 与注册验证码的区别：不检查邮箱是否已注册，允许向已注册邮箱发送
     * 使用场景：微信扫码后绑定邮箱，可能绑定已有账号或创建新账号
     */
    @Override
    public ResponseUtil sendBindCode(String email) {
        if (Objects.isNull(email) || email.trim().isEmpty()) {
            return new ResponseUtil(400, "邮箱不能为空", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return new ResponseUtil(400, "邮箱格式不正确", null);
        }
        // 60秒防刷（与 sendCode 共享同一个 Redis key，统一限流）
        String codeKey = EMAIL_CODE_PREFIX + email;
        String existingCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (Objects.nonNull(existingCode)) {
            Long ttl = stringRedisTemplate.getExpire(codeKey);
            if (Objects.nonNull(ttl) && ttl > 240) {
                return new ResponseUtil(400, "验证码已发送，" + (ttl - 240) + " 秒后可重新获取", null);
            }
        }
        String code = String.format("%06d", (int) (Math.random() * 1000000));
        stringRedisTemplate.opsForValue().set(codeKey, code, 5, TimeUnit.MINUTES);
        emailUtil.sendVerificationCode(email, code);
        return new ResponseUtil(200, "验证码已发送至邮箱，5分钟内有效", null);
    }

    /**
     * 邮箱验证码注册（无需图形验证码）
     */
    @Override
    public ResponseUtil register(String email, String password, String code) {
        if (Objects.isNull(email) || email.trim().isEmpty()
                || Objects.isNull(password) || password.trim().isEmpty()
                || Objects.isNull(code) || code.trim().isEmpty()) {
            return new ResponseUtil(400, "邮箱、密码和验证码不能为空", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return new ResponseUtil(400, "邮箱格式不正确", null);
        }
        if (password.length() < 6) {
            return new ResponseUtil(400, "密码不能少于6位", null);
        }
        // 邮箱验证码
        String codeKey = EMAIL_CODE_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (Objects.isNull(storedCode)) {
            return new ResponseUtil(400, "验证码已过期或未发送，请重新获取", null);
        }
        if (!storedCode.equals(code.trim())) {
            return new ResponseUtil(400, "验证码不正确", null);
        }
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (Objects.nonNull(exist)) {
            return new ResponseUtil(400, "该邮箱已注册", null);
        }
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);
        stringRedisTemplate.delete(codeKey);
        return new ResponseUtil(200, "注册成功", null);
    }

    /**
     * 登录（需图形验证码 + 失败次数限制）
     *
     * 防暴力破解：同一邮箱连续失败 5 次后锁定 15 分钟
     * 防用户枚举：无论用户不存在还是密码错误，统一返回"邮箱或密码错误"
     */
    @Override
    public ResponseUtil login(String email, String password, String captchaId, String captchaCode) {
        ResponseUtil captchaError = validateCaptcha(captchaId, captchaCode);
        if (Objects.nonNull(captchaError)) return captchaError;

        if (Objects.isNull(email) || Objects.isNull(password)) {
            return new ResponseUtil(400, "邮箱和密码不能为空", null);
        }

        // 检查是否已被锁定（防暴力破解）
        String failKey = LOGIN_FAIL_PREFIX + email;
        String failCountStr = stringRedisTemplate.opsForValue().get(failKey);
        if (Objects.nonNull(failCountStr) && Integer.parseInt(failCountStr) >= MAX_LOGIN_FAIL_COUNT) {
            Long ttl = stringRedisTemplate.getExpire(failKey);
            long remainingMinutes = Objects.nonNull(ttl) && ttl > 0 ? ttl / 60 + 1 : LOGIN_LOCK_MINUTES;
            return new ResponseUtil(429, "登录失败次数过多，请" + remainingMinutes + "分钟后再试", null);
        }

        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        // 统一错误提示，防止攻击者通过错误信息探测已注册邮箱（防用户枚举）
        if (Objects.isNull(user) || !passwordEncoder.matches(password, user.getPassword())) {
            // 记录失败次数
            recordLoginFail(email, failKey);
            return new ResponseUtil(400, "邮箱或密码错误", null);
        }

        // 登录成功，清除失败次数
        stringRedisTemplate.delete(failKey);

        // 单设备登录：踢掉旧设备
        String tokenKey = TOKEN_PREFIX + email;
        String refreshKey = REFRESH_TOKEN_PREFIX + email;
        String oldToken = stringRedisTemplate.opsForValue().get(tokenKey);
        if (Objects.nonNull(oldToken)) {
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.delete(refreshKey);
        }
        String accessToken = jwtUtil.generateAccessToken(email);
        String refreshToken = jwtUtil.generateRefreshToken(email);
        stringRedisTemplate.opsForValue().set(tokenKey, accessToken, 30, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(refreshKey, refreshToken, 7, TimeUnit.DAYS);
        Map<String, String> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        return new ResponseUtil(200, "登录成功", data);
    }

    /**
     * 记录登录失败次数，首次失败设置 15 分钟过期
     * 后续失败在原 TTL 上递增（不重置过期时间，防止攻击者通过短间隔规避锁定）
     */
    private void recordLoginFail(String email, String failKey) {
        Long increment = stringRedisTemplate.opsForValue().increment(failKey);
        if (Objects.nonNull(increment) && increment == 1) {
            stringRedisTemplate.expire(failKey, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
        }
    }

    @Override
    public ResponseUtil logout(String token) {
        String email;
        try {
            email = jwtUtil.getUsernameFromToken(token);
        } catch (Exception e) {
            return new ResponseUtil(400, "Token 无效", null);
        }
        stringRedisTemplate.delete(TOKEN_PREFIX + email);
        stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + email);
        return new ResponseUtil(200, "登出成功", null);
    }

    @Override
    public ResponseUtil refresh(String refreshToken) {
        if (Objects.isNull(refreshToken) || refreshToken.trim().isEmpty()) {
            return new ResponseUtil(400, "refreshToken 不能为空", null);
        }
        // 校验 Token 类型：必须是 refresh token，防止攻击者用 accessToken 无限续期
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            return new ResponseUtil(401, "Token 类型错误，请使用 refreshToken", null);
        }
        if (!jwtUtil.validateToken(refreshToken)) {
            return new ResponseUtil(401, "refreshToken 无效或已过期，请重新登录", null);
        }
        String email = jwtUtil.getUsernameFromToken(refreshToken);
        String refreshKey = REFRESH_TOKEN_PREFIX + email;
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(refreshKey);
        if (Objects.isNull(storedRefreshToken) || !storedRefreshToken.equals(refreshToken)) {
            return new ResponseUtil(401, "refreshToken 已失效，请重新登录", null);
        }
        String newAccessToken = jwtUtil.generateAccessToken(email);
        stringRedisTemplate.opsForValue().set(TOKEN_PREFIX + email, newAccessToken, 30, TimeUnit.MINUTES);
        Map<String, String> data = new HashMap<>();
        data.put("accessToken", newAccessToken);
        return new ResponseUtil(200, "Token 刷新成功", data);
    }

    // ==================== 微信扫码登录 ====================
    //
    // 完整 OAuth 2.0 流程：
    //   PC 浏览器                     手机微信                      后端
    //   ─────────                    ──────                       ────
    //   1. 展示二维码（微信授权URL）
    //       ↓                                                 → 2. 生成 sceneId
    //   3. 轮询检查状态 ──────────────────────────────────────→ 4. 返回 pending
    //                                5. 用户扫码
    //                                6. 用户确认授权
    //                                7. 微信回调               → 8. code→token→用户信息
    //                                                            9. 存 Redis(scanned)
    //   10. 轮询检测到 scanned → 显示邮箱绑定表单
    //   11. 用户输入邮箱+验证码 ──────────────────────────────→ 12. 创建/关联用户
    //                                                            13. 签发 Token(confirmed)
    //   14. 轮询检测到 confirmed → 自动登录

    /**
     * 生成微信扫码场景
     * 返回 sceneId 和真实的微信 OAuth 授权 URL（测试号 / 开放平台通用）
     * 场景 5 分钟有效
     */
    @Override
    public ResponseUtil wechatQrcode() {
        if (!wechatConfig.isConfigured()) {
            return new ResponseUtil(500, "微信登录未配置，请先在 application.yml 中设置 wechat.app-id 和 wechat.app-secret", null);
        }
        String sceneId = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(
                WECHAT_SCENE_PREFIX + sceneId,
                "pending",
                WECHAT_SCENE_TTL_MINUTES,
                TimeUnit.MINUTES);

        // 构造微信 OAuth 授权 URL
        // scope=snsapi_userinfo：获取用户昵称、头像（需用户手动授权，弹窗）
        // state=sceneId：防 CSRF + 关联 PC 端轮询会话
        String authUrl;
        try {
            authUrl = WechatConfig.OAUTH_AUTHORIZE_URL
                    + "?appid=" + wechatConfig.getAppId()
                    + "&redirect_uri=" + java.net.URLEncoder.encode(wechatConfig.getCallbackUrl(), "UTF-8")
                    + "&response_type=code"
                    + "&scope=snsapi_userinfo"
                    + "&state=" + sceneId
                    + "#wechat_redirect";
        } catch (Exception e) {
            return new ResponseUtil(500, "URL 编码失败", null);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("sceneId", sceneId);
        data.put("authUrl", authUrl);
        return new ResponseUtil(200, "微信扫码场景生成成功", data);
    }

    /**
     * 轮询检查扫码状态
     * pending → 等待扫码
     * scanned → 已扫码，返回微信用户信息（昵称、头像），前端显示邮箱绑定表单
     * confirmed → 已绑定邮箱，返回 Token，前端自动登录
     * expired → 二维码过期
     */
    @Override
    @SuppressWarnings("unchecked")
    public ResponseUtil wechatCheck(String sceneId) {
        if (Objects.isNull(sceneId) || sceneId.trim().isEmpty()) {
            return new ResponseUtil(400, "sceneId 不能为空", null);
        }
        String sceneKey = WECHAT_SCENE_PREFIX + sceneId;
        String status = stringRedisTemplate.opsForValue().get(sceneKey);
        if (Objects.isNull(status)) {
            return new ResponseUtil(400, "二维码已过期，请刷新", Map.of("status", "expired"));
        }

        Map<String, Object> data = new HashMap<>();
        data.put("status", status);

        if ("scanned".equals(status)) {
            // 已扫码：返回微信用户信息，前端展示邮箱绑定
            String dataJson = stringRedisTemplate.opsForValue().get(WECHAT_DATA_PREFIX + sceneId);
            if (Objects.nonNull(dataJson)) {
                try {
                    Map<String, Object> wxData = objectMapper.readValue(dataJson, Map.class);
                    data.put("nickname", wxData.getOrDefault("nickname", ""));
                    data.put("avatar", wxData.getOrDefault("avatar", ""));
                } catch (Exception ignored) {}
            }
        } else if ("confirmed".equals(status)) {
            // 已确认：返回 Token + 用户信息
            String tokenData = stringRedisTemplate.opsForValue().get(WECHAT_TOKEN_PREFIX + sceneId);
            if (Objects.nonNull(tokenData)) {
                String[] parts = tokenData.split("\\|");
                data.put("accessToken", parts[0]);
                if (parts.length > 1) data.put("refreshToken", parts[1]);
                if (parts.length > 2) data.put("email", parts[2]);
                if (parts.length > 3) data.put("nickname", parts[3]);
            }
        }
        return new ResponseUtil(200, "查询成功", data);
    }

    /**
     * 微信 OAuth 回调（手机浏览器访问）
     * 微信在用户确认授权后将 code 和 state 传回此端点
     * 后端完成 code → access_token → 用户信息 的交换链路
     */
    @Override
    public ResponseUtil wechatCallback(String code, String state) {
        if (Objects.isNull(code) || code.trim().isEmpty()) {
            return new ResponseUtil(400, "微信授权码不能为空", null);
        }
        if (Objects.isNull(state) || state.trim().isEmpty()) {
            return new ResponseUtil(400, "state 参数缺失，无法关联登录会话", null);
        }
        // 校验 sceneId 是否存在且为 pending
        String sceneKey = WECHAT_SCENE_PREFIX + state;
        String currentStatus = stringRedisTemplate.opsForValue().get(sceneKey);
        if (Objects.isNull(currentStatus)) {
            return new ResponseUtil(400, "二维码已过期，请返回重新操作", null);
        }
        if (!"pending".equals(currentStatus)) {
            return new ResponseUtil(400, "该二维码已被使用", null);
        }

        // Step 1: code → access_token + openid
        WechatUtil.WechatTokenResponse tokenResp = wechatUtil.getAccessToken(code);
        if (Objects.isNull(tokenResp) || Objects.isNull(tokenResp.getOpenid())) {
            return new ResponseUtil(500, "微信授权失败：无法获取 access_token", null);
        }
        String openid = tokenResp.getOpenid();

        // Step 2: access_token → 用户信息（昵称、头像）
        String nickname = "";
        String avatar = "";
        WechatUtil.WechatUserInfo userInfo = wechatUtil.getUserInfo(
                tokenResp.getAccessToken(), openid);
        if (Objects.nonNull(userInfo)) {
            nickname = Objects.toString(userInfo.getNickname(), "");
            avatar = Objects.toString(userInfo.getHeadImgUrl(), "");
        }

        // Step 2.5: 检查该微信是否已绑定账号 → 直接登录
        User boundUser = userMapper.selectOne(new QueryWrapper<User>().eq("openid", openid));
        if (Objects.nonNull(boundUser)) {
            // 更新最新的微信昵称和头像
            if (!nickname.isEmpty()) boundUser.setNickname(nickname);
            if (!avatar.isEmpty()) boundUser.setAvatar(avatar);
            userMapper.updateById(boundUser);

            // 签发 JWT Token
            String boundEmail = boundUser.getEmail();
            String tokenKey = TOKEN_PREFIX + boundEmail;
            String refreshKey = REFRESH_TOKEN_PREFIX + boundEmail;
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.delete(refreshKey);

            String accessToken = jwtUtil.generateAccessToken(boundEmail);
            String refreshToken = jwtUtil.generateRefreshToken(boundEmail);
            stringRedisTemplate.opsForValue().set(tokenKey, accessToken, 30, TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set(refreshKey, refreshToken, 7, TimeUnit.DAYS);

            // 存储 Token 供 PC 轮询获取
            stringRedisTemplate.opsForValue().set(
                    WECHAT_TOKEN_PREFIX + state,
                    accessToken + "|" + refreshToken + "|" + boundEmail + "|" + nickname,
                    WECHAT_SCENE_TTL_MINUTES,
                    TimeUnit.MINUTES);
            stringRedisTemplate.opsForValue().set(sceneKey, "confirmed", WECHAT_SCENE_TTL_MINUTES, TimeUnit.MINUTES);

            return new ResponseUtil(200, "登录成功", null);
        }

        // Step 3: 未绑定 → 存储微信用户信息到 Redis，状态改为 scanned（等待邮箱绑定）
        try {
            Map<String, String> wxData = new HashMap<>();
            wxData.put("openid", openid);
            wxData.put("nickname", nickname);
            wxData.put("avatar", avatar);
            stringRedisTemplate.opsForValue().set(
                    WECHAT_DATA_PREFIX + state,
                    objectMapper.writeValueAsString(wxData),
                    WECHAT_SCENE_TTL_MINUTES,
                    TimeUnit.MINUTES);
        } catch (Exception e) {
            return new ResponseUtil(500, "数据存储失败", null);
        }
        stringRedisTemplate.opsForValue().set(sceneKey, "scanned", WECHAT_SCENE_TTL_MINUTES, TimeUnit.MINUTES);

        return new ResponseUtil(200, "微信授权成功，请在电脑上继续完成邮箱绑定", null);
    }

    /**
     * 扫码后绑定邮箱（PC 端提交）
     *
     * 支持两种场景：
     *   1. 邮箱未注册 → 创建新用户（关联微信 openid）
     *   2. 邮箱已注册且未绑定微信 → 将微信 openid 关联到已有账号（账号升级）
     *   3. 邮箱已绑定其他微信 → 拒绝
     *
     * 参数：
     *   sceneId：扫码场景 ID
     *   email：待绑定的邮箱
     *   code：邮箱验证码
     */
    @Override
    @SuppressWarnings("unchecked")
    public ResponseUtil bindWechatEmail(String sceneId, String email, String code) {
        // ==== 参数校验 ====
        if (Objects.isNull(sceneId) || sceneId.trim().isEmpty()
                || Objects.isNull(email) || email.trim().isEmpty()
                || Objects.isNull(code) || code.trim().isEmpty()) {
            return new ResponseUtil(400, "sceneId、邮箱和验证码均不能为空", null);
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}$")) {
            return new ResponseUtil(400, "邮箱格式不正确", null);
        }

        // ==== 验证码校验 ====
        String codeKey = EMAIL_CODE_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (Objects.isNull(storedCode)) {
            return new ResponseUtil(400, "验证码已过期或未发送，请重新获取", null);
        }
        if (!storedCode.equals(code.trim())) {
            return new ResponseUtil(400, "验证码不正确", null);
        }

        // ==== 获取微信用户信息 ====
        String sceneKey = WECHAT_SCENE_PREFIX + sceneId;
        String currentStatus = stringRedisTemplate.opsForValue().get(sceneKey);
        if (!"scanned".equals(currentStatus)) {
            return new ResponseUtil(400, "请先使用微信扫码", null);
        }
        String dataJson = stringRedisTemplate.opsForValue().get(WECHAT_DATA_PREFIX + sceneId);
        if (Objects.isNull(dataJson)) {
            return new ResponseUtil(400, "微信授权信息已过期，请重新扫码", null);
        }
        Map<String, Object> wxData;
        try {
            wxData = objectMapper.readValue(dataJson, Map.class);
        } catch (Exception e) {
            return new ResponseUtil(500, "数据解析失败", null);
        }
        String openid = (String) wxData.get("openid");
        String nickname = (String) wxData.getOrDefault("nickname", "");
        String avatar = (String) wxData.getOrDefault("avatar", "");

        // ==== 账号匹配逻辑 ====
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("email", email));
        if (Objects.nonNull(user)) {
            // 邮箱已注册
            if (Objects.nonNull(user.getOpenid()) && !user.getOpenid().equals(openid)) {
                return new ResponseUtil(400, "该邮箱已绑定其他微信号，无法重复绑定", null);
            }
            // 更新微信信息（账号升级：邮箱注册 → 微信绑定）
            user.setOpenid(openid);
            if (!nickname.isEmpty()) user.setNickname(nickname);
            if (!avatar.isEmpty()) user.setAvatar(avatar);
            userMapper.updateById(user);
        } else {
            // 新用户：创建账号
            user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setOpenid(openid);
            user.setNickname(nickname);
            user.setAvatar(avatar);
            userMapper.insert(user);
        }

        // ==== 清理验证码 ====
        stringRedisTemplate.delete(codeKey);

        // ==== 签发 JWT Token ====
        String tokenKey = TOKEN_PREFIX + email;
        String refreshKey = REFRESH_TOKEN_PREFIX + email;
        stringRedisTemplate.delete(tokenKey);
        stringRedisTemplate.delete(refreshKey);

        String accessToken = jwtUtil.generateAccessToken(email);
        String refreshToken = jwtUtil.generateRefreshToken(email);
        stringRedisTemplate.opsForValue().set(tokenKey, accessToken, 30, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(refreshKey, refreshToken, 7, TimeUnit.DAYS);

        // ==== 更新场景状态 ====
        stringRedisTemplate.opsForValue().set(
                WECHAT_TOKEN_PREFIX + sceneId,
                accessToken + "|" + refreshToken,
                WECHAT_SCENE_TTL_MINUTES,
                TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(sceneKey, "confirmed", WECHAT_SCENE_TTL_MINUTES, TimeUnit.MINUTES);

        Map<String, String> data = new HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("email", email);
        data.put("nickname", nickname);
        return new ResponseUtil(200, "绑定成功，登录中...", data);
    }
}
