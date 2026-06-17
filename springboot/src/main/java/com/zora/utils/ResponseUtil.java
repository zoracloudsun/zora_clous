package com.zora.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "统一 API 响应包装")
public class ResponseUtil {
    @Schema(description = "状态码：200 成功，400 客户端错误，401 未授权，403 权限不足，429 请求过于频繁，500 服务器错误", example = "200")
    private Integer code;

    @Schema(description = "提示信息", example = "success")
    private String msg;

    @Schema(description = "响应数据（具体类型取决于接口）")
    private Object data;

    // Jackson 单例，用于 toJsonError 安全序列化（线程安全）
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ResponseUtil() {
    }

    public ResponseUtil(Integer code, String msg, Object data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    // ==================== 静态工厂方法 ====================

    /** 200 成功（无数据） */
    public static ResponseUtil success() {
        return new ResponseUtil(200, "success", null);
    }

    /** 200 成功（带数据） */
    public static ResponseUtil success(Object data) {
        return new ResponseUtil(200, "success", data);
    }

    /** 200 成功（自定义消息 + 数据） */
    public static ResponseUtil success(String msg, Object data) {
        return new ResponseUtil(200, msg, data);
    }

    /** 通用错误 */
    public static ResponseUtil error(Integer code, String msg) {
        return new ResponseUtil(code, msg, null);
    }

    /** 通用错误（带数据） */
    public static ResponseUtil error(Integer code, String msg, Object data) {
        return new ResponseUtil(code, msg, data);
    }

    // ==================== JSON 安全序列化 ====================

    /**
     * 将错误信息安全序列化为 JSON 字符串
     * 供拦截器 writeError 使用，替代易出错的字符串拼接
     *
     * @param code HTTP 状态码
     * @param msg  错误消息（可安全包含双引号、反斜杠等特殊字符）
     * @return JSON 字符串，如 {"code":401,"msg":"未携带 Token","data":null}
     */
    public static String toJsonError(int code, String msg) {
        try {
            java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("code", code);
            map.put("msg", msg);
            map.put("data", null);
            return MAPPER.writeValueAsString(map);
        } catch (Exception e) {
            // 序列化失败时的兜底（理论上不会发生）
            return "{\"code\":500,\"msg\":\"服务器内部错误\",\"data\":null}";
        }
    }

    // ==================== getter / setter ====================

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
