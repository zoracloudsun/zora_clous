package com.zyt.exception;

/**
 * 业务异常基类
 * 所有业务异常通过 @ControllerAdvice 全局捕获，统一返回 ResponseUtil JSON
 *
 * 语义子类（code 硬编码，调用方只需传 msg）：
 *   BadRequestException   → 400 参数校验失败
 *   UnauthorizedException → 401 未认证
 *   ForbiddenException    → 403 无权限
 *   NotFoundException     → 404 资源不存在
 *   RateLimitException    → 429 频率限制
 */
public class BusinessException extends RuntimeException {
    private final Integer code;
    private final Object data;

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.data = null;
    }

    public BusinessException(Integer code, String msg, Object data) {
        super(msg);
        this.code = code;
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
