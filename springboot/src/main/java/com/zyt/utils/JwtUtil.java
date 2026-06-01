package com.zyt.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成短期访问令牌（Access Token）
     * 用于日常 API 鉴权，有效期短（默认30分钟），降低泄露风险
     * JWT claims 中写入 type=access 区分令牌类型，防止混用
     */
    public String generateAccessToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("type", "access")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getKey())
                .compact();
    }

    /**
     * 生成长期刷新令牌（Refresh Token）
     * 仅用于换取新的 Access Token，有效期长（默认7天），避免频繁登录
     * JWT claims 中写入 type=refresh 区分令牌类型，防止当 Access Token 使用
     */
    public String generateRefreshToken(String username, String role) {
        return Jwts.builder()
                .subject(username)
                .claim("type", "refresh")
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getKey())
                .compact();
    }

    /**
     * 兼容旧版单 Token 调用，内部委托给 generateAccessToken
     */
    public String generateToken(String username, String role) {
        return generateAccessToken(username, role);
    }

    // 从 JWT 中提取用户名（subject）
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    // 检查 Token 是否过期（仅检查过期，不检查签名等其他问题）
    public boolean isTokenExpired(String token) {
        try {
            parseClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    // 全面验证 Token：签名正确性 + 是否过期 + 格式是否合法
    // 同时适用于 accessToken 和 refreshToken 的校验
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验 Token 是否为 refresh 类型，防止攻击者用 accessToken 冒充 refreshToken 无限续期
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

    /**
     * 从 JWT 中提取用户角色（role claim）
     */
    public String getRoleFromToken(String token) {
        return (String) parseClaims(token).get("role");
    }

    /**
     * 校验 Token 是否为 access 类型，用于拦截器二次确认（可选启用）
     */
    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseClaims(token);
            return "access".equals(claims.get("type"));
        } catch (Exception e) {
            return false;
        }
    }

    // 解析 JWT 并校验签名，jjwt 0.12.x API：verifyWith → build → parseSignedClaims
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
