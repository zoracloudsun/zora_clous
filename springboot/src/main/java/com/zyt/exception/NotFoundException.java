package com.zyt.exception;

/**
 * 404 Not Found — 资源不存在
 */
public class NotFoundException extends BusinessException {
    public NotFoundException(String msg) {
        super(404, msg);
    }
}
