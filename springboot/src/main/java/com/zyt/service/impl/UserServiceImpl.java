package com.zyt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zyt.entity.User;
import com.zyt.mapper.UserMapper;
import com.zyt.service.UserService;
import com.zyt.utils.JwtUtil;
import com.zyt.utils.ResponseUtil;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;
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

    // Redis key 前缀：accessToken 用 token: ，refreshToken 用 refresh_token:
    // 双 key 设计让两种 Token 有独立的生命周期，过期互不影响
    private static final String TOKEN_PREFIX = "token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    @Override
    public ResponseUtil register(String username, String password) {
        if (Objects.isNull(username) || username.trim().isEmpty()
                || Objects.isNull(password) || password.trim().isEmpty()) {
            return new ResponseUtil(400, "用户名或密码不能为空", null);
        }
        User exist = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (Objects.nonNull(exist)) {
            return new ResponseUtil(400, "用户名已存在", null);
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);
        return new ResponseUtil(200, "注册成功", null);
    }

    @Override
    public ResponseUtil login(String username, String password) {
        if (Objects.isNull(username) || Objects.isNull(password)) {
            return new ResponseUtil(400, "用户名或密码不能为空", null);
        }
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
        if (Objects.isNull(user)) {
            return new ResponseUtil(400, "用户名不存在", null);
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return new ResponseUtil(400, "密码错误", null);
        }
        // 单设备登录：同时删除旧的 accessToken 和 refreshToken（踢掉旧设备）
        String tokenKey = TOKEN_PREFIX + username;
        String refreshKey = REFRESH_TOKEN_PREFIX + username;
        String oldToken = stringRedisTemplate.opsForValue().get(tokenKey);
        if (Objects.nonNull(oldToken)) {
            stringRedisTemplate.delete(tokenKey);
            stringRedisTemplate.delete(refreshKey);
        }
        // 签发双 Token：
        // - accessToken：短期（30min），用于日常 API 鉴权，高频携带
        // - refreshToken：长期（7天），仅用于无感刷新 accessToken，低频使用
        String accessToken = jwtUtil.generateAccessToken(username);
        String refreshToken = jwtUtil.generateRefreshToken(username);
        // 两个 Token 分别存入 Redis，设置独立的 TTL，与 JWT 过期时间保持一致
        stringRedisTemplate.opsForValue().set(tokenKey, accessToken, 30, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(refreshKey, refreshToken, 7, TimeUnit.DAYS);
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        return new ResponseUtil(200, "登录成功", data);
    }

    @Override
    public ResponseUtil logout(String token) {
        String username;
        try {
            username = jwtUtil.getUsernameFromToken(token);
        } catch (Exception e) {
            return new ResponseUtil(400, "Token 无效", null);
        }
        String tokenKey = TOKEN_PREFIX + username;
        String refreshKey = REFRESH_TOKEN_PREFIX + username;
        stringRedisTemplate.delete(tokenKey);
        stringRedisTemplate.delete(refreshKey);
        return new ResponseUtil(200, "登出成功", null);
    }

    /**
     * 用 refreshToken 换取新的 accessToken（无感刷新）
     *
     * 校验链路：
     * 1. refreshToken 非空校验
     * 2. JWT 自校验（签名是否合法、是否过期）
     * 3. Redis 对比校验（是否被新登录踢掉或主动登出）
     *
     * 刷新时只生成新的 accessToken，refreshToken 保持不变，
     * 这样用户在 refreshToken 有效期内（7天）可以一直无感刷新，无需重新登录。
     */
    @Override
    public ResponseUtil refresh(String refreshToken) {
        if (Objects.isNull(refreshToken) || refreshToken.trim().isEmpty()) {
            return new ResponseUtil(400, "refreshToken 不能为空", null);
        }
        // JWT 层面校验：签名是否正确，是否过期
        if (!jwtUtil.validateToken(refreshToken)) {
            return new ResponseUtil(401, "refreshToken 无效或已过期，请重新登录", null);
        }
        // Redis 层面校验：是否被新登录踢掉（单设备登录）或主动登出
        String username = jwtUtil.getUsernameFromToken(refreshToken);
        String refreshKey = REFRESH_TOKEN_PREFIX + username;
        String storedRefreshToken = stringRedisTemplate.opsForValue().get(refreshKey);
        if (Objects.isNull(storedRefreshToken) || !storedRefreshToken.equals(refreshToken)) {
            return new ResponseUtil(401, "refreshToken 已失效，请重新登录", null);
        }
        // 只刷新 accessToken，refreshToken 保持不变（不续期）
        String newAccessToken = jwtUtil.generateAccessToken(username);
        stringRedisTemplate.opsForValue().set(TOKEN_PREFIX + username, newAccessToken, 30, TimeUnit.MINUTES);
        java.util.Map<String, String> data = new java.util.HashMap<>();
        data.put("accessToken", newAccessToken);
        return new ResponseUtil(200, "Token 刷新成功", data);
    }
}
