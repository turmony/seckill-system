package io.github.turmony.seckillsystem.service.Impl;

import cn.hutool.core.util.IdUtil;
import io.github.turmony.seckillsystem.common.RedisKeyConstant;
import io.github.turmony.seckillsystem.service.SecKillTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 秒杀令牌服务实现
 */
@Slf4j
@Service
public class SecKillTokenServiceImpl implements SecKillTokenService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 令牌过期时间（秒）- 5分钟
     */
    private static final long TOKEN_EXPIRE_SECONDS = 300;

    @Override
    public String generateToken(Long userId, Long goodsId) {
        // 生成唯一令牌（UUID）
        String token = IdUtil.fastSimpleUUID();

        // Redis Key: seckill:token:{userId}:{goodsId}
        String key = String.format(RedisKeyConstant.SECKILL_TOKEN_PREFIX, userId, goodsId);

        // 存储到Redis，过期时间5分钟
        stringRedisTemplate.opsForValue().set(key, token, TOKEN_EXPIRE_SECONDS, TimeUnit.SECONDS);

        log.info("生成秒杀令牌成功, userId:{}, goodsId:{}, token:{}", userId, goodsId, token);
        return token;
    }

    @Override
    public boolean validateAndConsumeToken(Long userId, Long goodsId, String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("令牌为空, userId:{}, goodsId:{}", userId, goodsId);
            return false;
        }

        String key = String.format(RedisKeyConstant.SECKILL_TOKEN_PREFIX, userId, goodsId);

        // 从Redis获取令牌
        String cachedToken = stringRedisTemplate.opsForValue().get(key);

        if (cachedToken == null) {
            log.warn("令牌不存在或已过期, userId:{}, goodsId:{}", userId, goodsId);
            return false;
        }

        // 验证令牌是否匹配
        if (!cachedToken.equals(token)) {
            log.warn("令牌不匹配, userId:{}, goodsId:{}, expected:{}, actual:{}",
                    userId, goodsId, cachedToken, token);
            return false;
        }

        // 验证通过，立即删除令牌（一次性使用）
        stringRedisTemplate.delete(key);
        log.info("令牌验证成功并已消费, userId:{}, goodsId:{}", userId, goodsId);

        return true;
    }

    @Override
    public void deleteToken(Long userId, Long goodsId) {
        String key = String.format(RedisKeyConstant.SECKILL_TOKEN_PREFIX, userId, goodsId);
        stringRedisTemplate.delete(key);
        log.info("删除秒杀令牌, userId:{}, goodsId:{}", userId, goodsId);
    }
}
