package com.zora.exception;

/**
 * 429 Too Many Requests — 频率限制 / 暴力破解锁定
 */
public class RateLimitException extends BusinessException {
    public RateLimitException(String msg) {
        super(429, msg);
    }
}
