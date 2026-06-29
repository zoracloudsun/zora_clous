package com.zora.exception;

/**
 * 业务异常基类
 * 所有业务异常通过 @ControllerAdvice 全局捕获，统一返回 ResponseUtil JSON
 *
 * 语义子类（code 硬编码，调用方只需传 msg）：
 * BadRequestException → 400 参数校验失败
 * UnauthorizedException → 401 未认证
 * ForbiddenException → 403 无权限
 * NotFoundException → 404 资源不存在
 * RateLimitException → 429 频率限制
 */
public class BusinessException extends RuntimeException {
    private final Integer code;
    private final Object data;
    private final ErrorCode errorCode;

    public BusinessException(Integer code, String msg) {
        super(msg);
        this.code = code;
        this.data = null;
        this.errorCode = null;
    }

    public BusinessException(Integer code, String msg, Object data) {
        super(msg);
        this.code = code;
        this.data = data;
        this.errorCode = null;
    }

    /**
     * 使用 ErrorCode 枚举的默认消息
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMsg());
        this.code = errorCode.getCode();
        this.data = null;
        this.errorCode = errorCode;
    }

    /**
     * ErrorCode + 自定义消息（覆盖默认消息）
     */
    public BusinessException(ErrorCode errorCode, String customMsg) {
        super(customMsg);
        this.code = errorCode.getCode();
        this.data = null;
        this.errorCode = errorCode;
    }

    public Integer getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }

    /**
     * 获取关联的错误码枚举（仅通过 ErrorCode 构造器创建时有值）
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
