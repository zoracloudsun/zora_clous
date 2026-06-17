package com.zora.exception;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.zora.utils.ResponseUtil;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 拦截所有 Controller 抛出的异常，统一转换为 ResponseUtil JSON 响应
 *
 * 设计原则：
 * 所有异常统一返回 HTTP 200，body.code 携带实际错误码。
 * 这样 Axios 成功拦截器统一用 res.code !== 200 判断并显示 res.msg。
 * 唯一的例外是 401（UnauthorizedException），必须返回 HTTP 401，
 * 因为前端 Axios 错误拦截器靠 response.status === 401 触发 Token 自动刷新。
 *
 * 异常处理优先级（Spring 自动选择最精确的 handler）：
 * 1. BusinessException 子类 → body.code 为异常的 code
 * 2. MethodArgumentNotValidException → 400 参数校验失败
 * 3. HttpMessageNotReadableException → 400 请求体格式错误
 * 4. HttpRequestMethodNotSupportedException → 405 请求方法不支持
 * 5. Exception → 500 兜底（记录完整堆栈，不暴露细节给客户端）
 *
 * 注意：拦截器（LoginInterceptor / RoleInterceptor）在请求进入 Controller 之前执行，
 * 其错误响应不经过此处理器，仍由各自的 writeError 方法直接写入 HttpServletResponse。
 */
@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 业务异常 ====================

    /**
     * 所有 BusinessException 及其子类的统一处理
     * 只有 401 返回真实 HTTP 401（前端 Axios 自动刷新依赖 response.status === 401）
     * 其他业务异常返回 HTTP 200，body.code 携带实际错误码，走 Axios 成功拦截器统一处理
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ResponseUtil> handleBusinessException(BusinessException ex) {
        ResponseUtil body = new ResponseUtil(ex.getCode(), ex.getMessage(), ex.getData());
        if (ex.getCode() == 401) {
            return ResponseEntity.status(401).body(body);
        }
        return ResponseEntity.ok(body);
    }

    // ==================== Spring MVC 内置异常 ====================

    /**
     * 参数校验失败（@Valid 校验，预留）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResponseUtil> handleValidation(MethodArgumentNotValidException ex) {
        StringBuilder sb = new StringBuilder("参数校验失败: ");
        ex.getBindingResult().getFieldErrors().forEach(
                error -> sb.append(error.getField()).append(" ").append(error.getDefaultMessage()).append(", "));
        // 去掉末尾的 ", "
        String msg = sb.substring(0, sb.length() - 2);
        return ResponseEntity.ok(ResponseUtil.error(400, msg));
    }

    /**
     * 请求体 JSON 格式错误（无法反序列化）
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ResponseUtil> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.ok(ResponseUtil.error(400, "请求体格式错误"));
    }

    /**
     * HTTP 方法不支持（GET 访问 POST 接口等）
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ResponseUtil> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.ok(ResponseUtil.error(405, "请求方法不支持"));
    }

    // ==================== 静态资源缺失（favicon.ico 等，无需记录） ====================

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ResponseUtil> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.ok(ResponseUtil.error(404, "资源不存在"));
    }

    // ==================== 兜底处理 ====================

    /**
     * 未预期的异常 — 记录完整堆栈，返回通用 500 错误
     * 绝不暴露异常细节给客户端（安全考量）
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseUtil> handleException(Exception ex) {
        log.error("未捕获的服务器异常", ex);
        return ResponseEntity.ok(ResponseUtil.error(500, "服务器内部错误"));
    }
}
