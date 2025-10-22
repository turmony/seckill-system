package io.github.turmony.seckillsystem.common;

/**
 * Redis Key常量类
 * 统一管理Redis中使用的Key
 */
public class RedisKeyConstant {
    /**
     * 秒杀令牌Key前缀: seckill:token:{userId}:{goodsId}
     */
    public static final String SECKILL_TOKEN_PREFIX = "seckill:token:%s:%s";

    /**
     * 秒杀商品缓存Key前缀
     * 完整格式: seckill:goods:{goodsId}
     */
    public static final String SECKILL_GOODS_PREFIX = "seckill:goods:";

    /**
     * 秒杀商品库存Key前缀
     * 完整格式: seckill:stock:{goodsId}
     */
    public static final String SECKILL_STOCK_PREFIX = "seckill:stock:";

    /**
     * 秒杀商品列表Key
     * 用于缓存所有秒杀商品ID列表
     */
    public static final String SECKILL_GOODS_LIST = "seckill:goods:list";


    // 在 RedisKeyConstant 类中添加以下常量和方法：

    /**
     * 秒杀订单分布式锁Key前缀
     * 格式: seckill:lock:userId:goodsId
     */
    private static final String SECKILL_ORDER_LOCK = "seckill:lock:";

    /**
     * 获取秒杀订单分布式锁Key
     * 锁的粒度：用户ID + 商品ID（防止同一用户对同一商品重复下单）
     *
     * @param userId  用户ID
     * @param goodsId 商品ID
     * @return 锁Key
     */
    public static String getSeckillOrderLockKey(Long userId, Long goodsId) {
        return SECKILL_ORDER_LOCK + userId + ":" + goodsId;
    }

    /**
     * 获取秒杀商品缓存Key
     * @param goodsId 商品ID
     * @return Redis Key
     */
    public static String getSeckillGoodsKey(Long goodsId) {
        return SECKILL_GOODS_PREFIX + goodsId;
    }

    /**
     * 获取秒杀库存Key
     * @param goodsId 商品ID
     * @return Redis Key
     */
    public static String getSeckillStockKey(Long goodsId) {
        return SECKILL_STOCK_PREFIX + goodsId;
    }

    /**
     * 获取秒杀令牌Key
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return Redis Key
     */
    public static String getSeckillTokenKey(Long userId, Long goodsId) {
        return SECKILL_TOKEN_PREFIX + userId + ":" + goodsId;
    }
}