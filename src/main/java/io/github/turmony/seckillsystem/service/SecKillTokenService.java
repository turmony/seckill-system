package io.github.turmony.seckillsystem.service;

/**
 * 秒杀令牌服务
 */
public interface SecKillTokenService {

    /**
     * 生成秒杀令牌
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 令牌字符串
     */
    String generateToken(Long userId, Long goodsId);

    /**
     * 验证并消费令牌（一次性）
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @param token 令牌
     * @return 验证是否成功
     */
    boolean validateAndConsumeToken(Long userId, Long goodsId, String token);

    /**
     * 删除令牌
     * @param userId 用户ID
     * @param goodsId 商品ID
     */
    void deleteToken(Long userId, Long goodsId);
}
