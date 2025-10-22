package io.github.turmony.seckillsystem.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson配置类
 * 用于创建RedissonClient实例，提供分布式锁功能
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private Integer redisPort;

    @Value("${spring.redis.password:}")
    private String redisPassword;

    @Value("${spring.redis.database:0}")
    private Integer database;

    @Value("${spring.redis.timeout:3000}")
    private Integer timeout;

    /**
     * 创建RedissonClient Bean
     * 支持单机模式，如需集群模式可扩展
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 单机模式配置
        String address = "redis://" + redisHost + ":" + redisPort;
        config.useSingleServer()
                .setAddress(address)
                .setDatabase(database)
                .setTimeout(timeout)
                .setConnectionPoolSize(64)        // 连接池大小
                .setConnectionMinimumIdleSize(10) // 最小空闲连接
                .setRetryAttempts(3)              // 重试次数
                .setRetryInterval(1500);          // 重试间隔(ms)

        // 如果Redis设置了密码
        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }

        return Redisson.create(config);
    }
}
