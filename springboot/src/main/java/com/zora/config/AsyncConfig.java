package com.zora.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池配置
 * <p>
 * Phase 4 用户行为分析 —— 为 {@code @Async} 提供专用的线程池，
 * 用于行为日志的异步写入，替代 Spring 默认的 SimpleAsyncTaskExecutor（每次新建线程），
 * 确保线程资源可控。
 * </p>
 *
 * <h3>线程池参数</h3>
 * <ul>
 * <li>核心线程数: 2（日常足够，行为日志写入非常轻量）</li>
 * <li>最大线程数: 5（高峰期上限）</li>
 * <li>队列容量: 100（缓冲峰值请求）</li>
 * <li>拒绝策略: CallerRunsPolicy（队列满了由调用线程执行，不丢日志）</li>
 * </ul>
 *
 * <p>注意：{@code AppStart.java} 上已有 {@code @EnableAsync}，
 * 本配置类为 Bean 级别的线程池定制。两个 {@code @EnableAsync} 可共存，
 * 这里的 {@link AsyncConfigurer#getAsyncExecutor()} 会覆盖默认的 executor。</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("action-log-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
