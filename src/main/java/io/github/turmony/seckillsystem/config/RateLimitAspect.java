package io.github.turmony.seckillsystem.config;

import com.google.common.util.concurrent.RateLimiter;
import io.github.turmony.seckillsystem.common.RateLimit;
import io.github.turmony.seckillsystem.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流切面
 * 基于Guava的RateLimiter实现接口限流
 *
 * @author turmony
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    /**
     * 存储每个接口的限流器
     * Key: 方法全限定名
     * Value: RateLimiter实例
     */
    private final ConcurrentHashMap<String, RateLimiter> rateLimiterMap = new ConcurrentHashMap<>();

    /**
     * 环绕通知，处理限流逻辑
     */
    @Around("@annotation(io.github.turmony.seckillsystem.common.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取RateLimit注解
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        // 获取方法全限定名作为key
        String key = method.getDeclaringClass().getName() + "." + method.getName();

        // 获取或创建限流器
        RateLimiter rateLimiter = rateLimiterMap.computeIfAbsent(key,
                k -> RateLimiter.create(rateLimit.permitsPerSecond()));

        // 尝试获取令牌
        boolean acquired = rateLimiter.tryAcquire();

        if (!acquired) {
            // 限流触发，返回失败响应
            log.warn("接口限流触发: {}, 限流配置: {}请求/秒", key, rateLimit.permitsPerSecond());
            return Result.error(rateLimit.message());
        }

        // 获取令牌成功，执行方法
        return joinPoint.proceed();
    }
}