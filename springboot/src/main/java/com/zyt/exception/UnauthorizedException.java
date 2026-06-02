package com.zyt.exception;

/**
 * 401 Unauthorized — 未登录或 Token 无效
 */
public class UnauthorizedException extends BusinessException {
    public UnauthorizedException(String msg) {
        super(401, msg);
    }
}
