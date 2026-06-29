package com.zora.exception;

/**
 * 业务异常码枚举
 * <p>只收拢跨 Service 重复抛出的异常码。不全量枚举所有错误场景——
 * 一次性校验和冷门错误直接用 {@code new BadRequestException("xxx")}。</p>
 *
 * <p>使用方式:
 * <pre>{@code
 * throw new BusinessException(ErrorCode.KB_NOT_FOUND);
 * throw new BusinessException(ErrorCode.CONV_NOT_FOUND, "对话 ID=" + id);
 * }</pre>
 * </p>
 */
public enum ErrorCode {

    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    RATE_LIMITED(429, "操作太频繁"),
    CONV_NOT_FOUND(404, "对话不存在"),
    KB_NOT_FOUND(404, "知识库不存在"),
    DOC_NOT_FOUND(404, "文档不存在"),
    ;

    private final int code;
    private final String defaultMsg;

    ErrorCode(int code, String defaultMsg) {
        this.code = code;
        this.defaultMsg = defaultMsg;
    }

    public int getCode() { return code; }
    public String getDefaultMsg() { return defaultMsg; }
}
