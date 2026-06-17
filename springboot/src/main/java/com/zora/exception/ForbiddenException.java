package com.zora.exception;

/**
 * 403 Forbidden — 权限不足
 */
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String msg) {
        super(403, msg);
    }
}
