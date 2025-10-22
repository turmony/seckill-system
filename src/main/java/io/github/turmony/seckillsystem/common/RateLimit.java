package io.github.turmony.seckillsystem.common;

import java.lang.annotation.*;

/**
 * 限流注解
 * 使用Guava的RateLimiter实现接口限流
 *
 * @author turmony
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 每秒允许的请求数量
     * 默认1000个请求/秒
     */
    double permitsPerSecond() default 1000.0;

    /**
     * 限流失败时的提示信息
     */
    String message() default "系统繁忙，请稍后重试";
}