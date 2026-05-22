package com.zyt.config;

import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 相关配置类
 * 用于配置和注册项目所需的 MyBatis-Plus 插件
 */
@Configuration
public class MyConfig {

    /**
     * 注册 MyBatis-Plus 分页插件
     * 将该拦截器交给 Spring 容器管理后，即可在项目中自动实现分页查询功能
     *
     * @return 分页拦截器实例
     */
    @Bean
    public PaginationInnerInterceptor paginationInterceptor() {
        return new PaginationInnerInterceptor();
    }
}