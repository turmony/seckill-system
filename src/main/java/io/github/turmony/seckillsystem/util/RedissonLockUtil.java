package io.github.turmony.seckillsystem.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redisson分布式锁工具类
 * 封装常用的分布式锁操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedissonLockUtil {

    private final RedissonClient redissonClient;

    /**
     * 加锁（默认超时时间10秒）
     *
     * @param lockKey 锁的key
     * @return true-加锁成功, false-加锁失败
     */
    public boolean tryLock(String lockKey) {
        return tryLock(lockKey, 0, 10, TimeUnit.SECONDS);
    }

    /**
     * 加锁（自定义等待时间和超时时间）
     *
     * @param lockKey   锁的key
     * @param waitTime  等待获取锁的时间
     * @param leaseTime 锁的超时时间（自动释放）
     * @param unit      时间单位
     * @return true-加锁成功, false-加锁失败
     */
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, unit);
            if (locked) {
                log.debug("获取分布式锁成功, lockKey: {}", lockKey);
            } else {
                log.warn("获取分布式锁失败, lockKey: {}", lockKey);
            }
            return locked;
        } catch (InterruptedException e) {
            log.error("获取分布式锁异常, lockKey: {}, error: {}", lockKey, e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 释放锁
     *
     * @param lockKey 锁的key
     */
    public void unlock(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        // 只有当前线程持有锁时才释放
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
            log.debug("释放分布式锁成功, lockKey: {}", lockKey);
        } else {
            log.warn("当前线程未持有锁，无需释放, lockKey: {}", lockKey);
        }
    }

    /**
     * 尝试加锁并执行业务逻辑（推荐使用）
     * 自动处理加锁、释放锁、异常处理
     *
     * @param lockKey  锁的key
     * @param supplier 需要执行的业务逻辑
     * @param <T>      返回值类型
     * @return 业务逻辑的返回值
     * @throws RuntimeException 如果获取锁失败或执行异常
     */
    public <T> T executeWithLock(String lockKey, LockCallback<T> supplier) {
        return executeWithLock(lockKey, 0, 10, TimeUnit.SECONDS, supplier);
    }

    /**
     * 尝试加锁并执行业务逻辑（自定义超时时间）
     *
     * @param lockKey   锁的key
     * @param waitTime  等待获取锁的时间
     * @param leaseTime 锁的超时时间
     * @param unit      时间单位
     * @param callback  需要执行的业务逻辑
     * @param <T>       返回值类型
     * @return 业务逻辑的返回值
     */
    public <T> T executeWithLock(String lockKey, long waitTime, long leaseTime,
                                 TimeUnit unit, LockCallback<T> callback) {
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean locked = lock.tryLock(waitTime, leaseTime, unit);
            if (!locked) {
                log.warn("获取分布式锁失败, lockKey: {}", lockKey);
                throw new RuntimeException("系统繁忙，请稍后重试");
            }

            log.debug("获取分布式锁成功, lockKey: {}", lockKey);

            // 执行业务逻辑
            return callback.execute();

        } catch (InterruptedException e) {
            log.error("获取分布式锁异常, lockKey: {}, error: {}", lockKey, e.getMessage());
            Thread.currentThread().interrupt();
            throw new RuntimeException("系统异常，请稍后重试");
        } finally {
            // 确保锁被释放
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁成功, lockKey: {}", lockKey);
            }
        }
    }

    /**
     * 判断锁是否被占用
     *
     * @param lockKey 锁的key
     * @return true-已被占用, false-未被占用
     */
    public boolean isLocked(String lockKey) {
        RLock lock = redissonClient.getLock(lockKey);
        return lock.isLocked();
    }

    /**
     * 锁回调接口
     * 用于executeWithLock方法
     */
    @FunctionalInterface
    public interface LockCallback<T> {
        /**
         * 执行业务逻辑
         *
         * @return 业务结果
         */
        T execute();
    }
}