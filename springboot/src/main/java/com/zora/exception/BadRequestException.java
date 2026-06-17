package com.zora.exception;

/**
 * 400 Bad Request — 参数校验失败、业务规则拒绝
 */
public class BadRequestException extends BusinessException {
    public BadRequestException(String msg) {
        super(400, msg);
    }
}
