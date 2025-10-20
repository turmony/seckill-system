package io.github.turmony.seckillsystem.common;


/**
 * Redis Key常量类
 * 统一管理Redis中使用的Key
 */
public class RedisKeyConstant {

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
}
