package com.zora.config.auth;

import java.lang.annotation.*;

/**
 * 自定义角色权限注解
 * 标记在 Controller 方法或类上，指定访问所需的角色
 *
 * 用法：
 * @RequireRole("admin") — 仅 admin 可访问
 * 方法级注解优先于类级注解
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireRole {
    /** 所需角色，如 "admin" */
    String value();
}
