# P0 安全漏洞修复记录

> 修复日期：2026-05-31  
> 修复人：hualuoshixiangyui  
> 项目：Spring Boot Auth System（前后端分离项目）

---

## P0-1：Refresh 接口缺少 Token 类型校验

### 漏洞描述

`/user/refresh` 接口接受客户端传入的 `refreshToken` 换取新的 `accessToken`，但只校验了 JWT 签名和过期时间，**没有校验 JWT claims 中的 `type` 字段**。

攻击者可以：
1. 获取一个 `accessToken`（可通过网络抓包、XSS 等方式）
2. 将该 `accessToken` 作为 `refreshToken` 发送到 `/user/refresh`
3. 获得一个新的 `accessToken`，实现**无限续期**，即使原 `accessToken` 在 Redis 中已经被删除（登出/被踢）

### 根本原因

JWT 生成时已经写入了 `type` claim 用于区分令牌用途：

```java
// JwtUtil.java - generateAccessToken
.claim("type", "access")

// JwtUtil.java - generateRefreshToken
.claim("type", "refresh")
```

但 `validateToken()` 只校验签名 + 过期，不读取 `type` 字段。claim 形同虚设。

### 修复方案

1. **JwtUtil.java** — 新增两个方法：

```java
/**
 * 校验 Token 是否为 refresh 类型
 * 只有 type=refresh 的 Token 才能调用 /user/refresh 换取新 accessToken
 */
public boolean isRefreshToken(String token) {
    try {
        Claims claims = parseClaims(token);
        return "refresh".equals(claims.get("type"));
    } catch (Exception e) {
        return false;
    }
}

public boolean isAccessToken(String token) { ... }  // 备用
```

2. **UserServiceImpl.java** — `refresh()` 方法增加类型校验：

```java
// 校验 Token 类型：必须是 refresh token，防止攻击者用 accessToken 无限续期
if (!jwtUtil.isRefreshToken(refreshToken)) {
    return new ResponseUtil(401, "Token 类型错误，请使用 refreshToken", null);
}
```

校验顺序：`类型校验 → 签名校验 → Redis 比对`，类型错误在前可以提前拒绝，减少不必要的签名运算。

### 安全效果

| 攻击场景 | 修复前 | 修复后 |
|----------|--------|--------|
| 用 accessToken 调用 /refresh | ✅ 能成功续期 | ❌ 返回 "Token 类型错误" |
| 用伪造 Token 调用 /refresh | ❌ 签名校验拦截 | ❌ 签名校验拦截 |
| 用过期 refreshToken 调用 /refresh | ❌ 签名校验拦截 | ❌ 类型或签名校验拦截 |

---

## P0-2：登录接口缺少失败次数限制（暴力破解防护）

### 漏洞描述

`/user/login` 接口对同一邮箱的连续登录失败没有任何限制。攻击者可以：
1. 通过社工或泄露数据库获知目标邮箱
2. 使用字典/撞库脚本无限尝试密码
3. 即使有图形验证码保护，攻击者可通过 OCR 或打码平台绕过

### 附带漏洞：用户枚举

修复前登录接口对不同情况返回不同错误信息：
- 用户不存在 → `"用户不存在"`
- 密码错误 → `"密码错误"`

攻击者可以通过返回信息判断哪些邮箱已注册，实现**用户枚举攻击**。

### 修复方案

**UserServiceImpl.java** — `login()` 方法重构：

```java
// 1. 检查是否已被锁定
String failKey = LOGIN_FAIL_PREFIX + email;
String failCountStr = stringRedisTemplate.opsForValue().get(failKey);
if (failCountStr != null && Integer.parseInt(failCountStr) >= MAX_LOGIN_FAIL_COUNT) {
    // 返回锁定提示 + 剩余时间
    return new ResponseUtil(429, "登录失败次数过多，请" + remainingMinutes + "分钟后再试", null);
}

// 2. 统一错误提示（防用户枚举）
if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
    recordLoginFail(email, failKey);
    return new ResponseUtil(400, "邮箱或密码错误", null);
}

// 3. 登录成功后清除失败记录
stringRedisTemplate.delete(failKey);
```

新增 `recordLoginFail()` 辅助方法：

```java
private void recordLoginFail(String email, String failKey) {
    Long increment = stringRedisTemplate.opsForValue().increment(failKey);
    if (increment != null && increment == 1) {
        // 首次失败设置 15 分钟 TTL，后续失败不重置 TTL
        stringRedisTemplate.expire(failKey, LOGIN_LOCK_MINUTES, TimeUnit.MINUTES);
    }
}
```

### 关键设计决策

| 决策 | 说明 |
|------|------|
| 使用 Redis `INCR`（原子操作） | 避免并发竞态导致计数不准确 |
| TTL 仅在首次失败时设置 | 防止攻击者通过控制失败间隔来重置锁定计时器 |
| 锁定时间 = 15 分钟 | 平衡安全性和用户体验 |
| 最大失败次数 = 5 | 留有足够的容错空间 |
| HTTP 状态码 429 | Too Many Requests，语义正确，前端拦截器可识别 |
| 成功登录后清除计数 | 用户记起正确密码后不需要等待锁定过期 |

### 安全效果

| 攻击场景 | 修复前 | 修复后 |
|----------|--------|--------|
| 连续 5 次密码错误 | 可继续尝试 | 锁定 15 分钟 |
| 尝试探测已注册邮箱 | 可区分"不存在"/"密码错" | 统一提示 "邮箱或密码错误" |
| 间隔 14 分钟后重试 | — | 失败计数不重置，仍需等满 15 分钟 |
| 第 5 次输入正确密码 | — | 登录成功，清除失败计数 |

---

## 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| [`springboot/src/main/java/com/zyt/utils/JwtUtil.java`](springboot/src/main/java/com/zyt/utils/JwtUtil.java) | 新增 `isRefreshToken()` 和 `isAccessToken()` 方法 |
| [`springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java`](springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java) | 1. `refresh()` 增加 Token 类型校验<br>2. `login()` 增加失败次数限制 + 统一错误提示<br>3. 新增 `recordLoginFail()` 方法<br>4. 新增常量 `LOGIN_FAIL_PREFIX`、`MAX_LOGIN_FAIL_COUNT`、`LOGIN_LOCK_MINUTES` |

---

## 后续建议（P1）

- [ ] 登录拦截器 `LoginInterceptor` 可考虑增加 `isAccessToken()` 类型校验（双保险）
- [ ] 图形验证码输错后保留 3 次机会再删除 key（当前是输错一次就删）
- [ ] 密码强度校验增加大小写字母 + 数字 + 特殊字符要求
- [ ] 找回密码功能（邮箱验证码 → 重置密码）

---

## P1：微信扫码登录功能完善

> 修复日期：2026-05-31

### 问题描述

[Login.vue](web/frontend/src/views/Login.vue) 的"微信登录"选项卡显示 `微信扫码功能开发中`，是一个硬编码的占位符，面试官看到会直接判定功能未完成。

### 架构设计

真实微信扫码登录基于 OAuth 2.0 协议，流程如下：

```
前端生成二维码 → 用户微信扫码 → 微信回调 redirect_uri?code=xxx
→ 后端用 code 换 access_token → 换 openid/unionid → 创建/关联用户 → 签发 JWT
```

由于微信开放平台需要企业资质才能获取 AppID，当前采用**完整架构 + 模拟演示**方案：
- 后端完整实现场景生成、状态轮询、扫码确认流程
- 前端完整实现二维码渲染、状态展示、轮询检测
- "模拟微信扫码登录"按钮替代真实微信回调，后续有了 AppID 只需改 2 处代码即可切换

### 修改清单

| 文件 | 修改内容 |
|------|----------|
| [`springboot/src/main/java/com/zyt/service/UserService.java`](springboot/src/main/java/com/zyt/service/UserService.java) | 新增 3 个接口方法签名：`wechatQrcode()`、`wechatCheck()`、`wechatSimulate()` |
| [`springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java`](springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java) | 新增 3 个微信登录方法实现（约 100 行），含完整注释说明真实 OAuth 流程 |
| [`springboot/src/main/java/com/zyt/controller/UserController.java`](springboot/src/main/java/com/zyt/controller/UserController.java) | 新增 3 个 REST 端点：`POST /user/wechat/qrcode`、`GET /user/wechat/check`、`POST /user/wechat/simulate` |
| [`springboot/src/main/java/com/zyt/config/WebConfig.java`](springboot/src/main/java/com/zyt/config/WebConfig.java) | 拦截器白名单新增 3 个微信端点 |
| [`web/frontend/src/api/user.js`](web/frontend/src/api/user.js) | 新增 `getWechatQrcode()`、`checkWechatScan()`、`simulateWechatScan()` |
| [`web/frontend/src/views/Login.vue`](web/frontend/src/views/Login.vue) | 完全重写微信登录 Tab：二维码渲染 → 状态轮询 → 模拟扫码 → 自动登录 |
| [`web/frontend/package.json`](web/frontend/package.json) | 新增依赖 `qrcode`（前端二维码库） |

### API 设计

| 端点 | 方法 | 说明 | 鉴权 |
|------|------|------|------|
| `/user/wechat/qrcode` | POST | 生成扫码场景，返回 sceneId + authUrl | 无需登录 |
| `/user/wechat/check?sceneId=xxx` | GET | 轮询扫码状态：pending / scanned / confirmed / expired | 无需登录 |
| `/user/wechat/simulate?sceneId=xxx` | POST | 模拟微信扫码确认（演示用） | 无需登录 |

### 前端状态机

```
'' (初始)
  ↓ 切换到微信 Tab
'pending'（二维码已生成，等待扫码）
  ↓ 模拟扫码点击                    ↓ 轮询检测到 confirmed
'confirmed'（签发 Token，自动登录）  →  跳转 /home
  ↓ 二维码过期（5分钟）
'expired'（点击刷新重新生成）
```

### 切换到真实微信 OAuth

只需要 2 处修改：

1. **UserServiceImpl.wechatQrcode()** — 将 `authUrl` 替换为真实微信授权 URL：
```java
data.put("authUrl", "https://open.weixin.qq.com/connect/qrconnect?"
    + "appid=" + wechatAppId
    + "&redirect_uri=" + URLEncoder.encode(callbackUrl, "UTF-8")
    + "&response_type=code"
    + "&scope=snsapi_login"
    + "&state=" + sceneId);
```

2. **新增回调端点** — `GET /user/wechat/callback?code=xxx&state=yyy`：
   - 用 `code` 调用微信 API 换取 `access_token` 和 `openid`
   - 更新 Redis 场景状态为 `confirmed`
   - 创建/关联用户 → 签发 Token
   - 返回一个确认页面（或 JSON）

3. **删除模拟端点** — 移除 `/user/wechat/simulate` 及其前端调用

### 技术要点

- 二维码使用 `qrcode` 库在前端渲染为 Canvas → base64，无需服务端生成图片
- 轮询间隔 2 秒，`confirmed` 后自动停止，组件销毁时 `onUnmounted` 清理定时器
- Redis 场景数据 5 分钟 TTL，过期后自动清除
- 演示用户邮箱格式：`wx_{openid}@wechat.local`，密码随机生成，与正常注册用户隔离
- 微信 Tab 切换时自动触发二维码生成（通过 `watch(activeTab)` 实现）

---

## 累计修改文件汇总

| 文件 | P0-1 | P0-2 | P1 微信 |
|------|:----:|:----:|:-------:|
| [`JwtUtil.java`](springboot/src/main/java/com/zyt/utils/JwtUtil.java) | ✅ | — | — |
| [`UserService.java`](springboot/src/main/java/com/zyt/service/UserService.java) | — | — | ✅ |
| [`UserServiceImpl.java`](springboot/src/main/java/com/zyt/service/impl/UserServiceImpl.java) | ✅ | ✅ | ✅ |
| [`UserController.java`](springboot/src/main/java/com/zyt/controller/UserController.java) | — | — | ✅ |
| [`WebConfig.java`](springboot/src/main/java/com/zyt/config/WebConfig.java) | — | — | ✅ |
| [`api/user.js`](web/frontend/src/api/user.js) | — | — | ✅ |
| [`Login.vue`](web/frontend/src/views/Login.vue) | — | — | ✅ |
| [`package.json`](web/frontend/package.json) | — | — | ✅（新增 qrcode 依赖） |
