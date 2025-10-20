package io.github.turmony.seckillsystem.config;


import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis配置类
 */
@Configuration
public class RedisConfig {

    /**
     * 配置RedisTemplate
     * 设置序列化方式：Key使用String序列化，Value使用FastJson序列化
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 使用FastJson序列化器
        FastJsonRedisSerializer<Object> fastJsonRedisSerializer = new FastJsonRedisSerializer<>(Object.class);

        // 使用String序列化器
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Key采用String的序列化方式
        redisTemplate.setKeySerializer(stringRedisSerializer);
        // Hash的Key也采用String的序列化方式
        redisTemplate.setHashKeySerializer(stringRedisSerializer);

        // Value采用FastJson序列化方式
        redisTemplate.setValueSerializer(fastJsonRedisSerializer);
        // Hash的Value也采用FastJson序列化方式
        redisTemplate.setHashValueSerializer(fastJsonRedisSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
