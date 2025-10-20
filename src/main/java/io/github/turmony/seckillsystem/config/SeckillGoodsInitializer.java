package io.github.turmony.seckillsystem.config;


import io.github.turmony.seckillsystem.common.RedisKeyConstant;
import io.github.turmony.seckillsystem.entity.SeckillGoods;
import io.github.turmony.seckillsystem.mapper.SeckillGoodsMapper;
import io.github.turmony.seckillsystem.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀商品初始化器
 * 项目启动时将秒杀商品信息和库存预加载到Redis
 */
@Slf4j
@Component
public class SeckillGoodsInitializer implements CommandLineRunner {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Override
    public void run(String... args) throws Exception {
        log.info("========== 开始预加载秒杀商品到Redis ==========");

        try {
            // 1. 从数据库查询所有秒杀商品
            List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);

            if (seckillGoodsList == null || seckillGoodsList.isEmpty()) {
                log.warn("数据库中没有秒杀商品数据");
                return;
            }

            // 2. 将秒杀商品信息和库存加载到Redis
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                // 商品信息缓存Key
                String goodsKey = RedisKeyConstant.getSeckillGoodsKey(seckillGoods.getGoodsId());
                // 库存缓存Key
                String stockKey = RedisKeyConstant.getSeckillStockKey(seckillGoods.getGoodsId());

                // 缓存秒杀商品信息（包含完整对象）
                redisUtil.set(goodsKey, seckillGoods);

                // 缓存库存数量（单独存储，方便后续Lua脚本操作）
                redisUtil.set(stockKey, seckillGoods.getStockCount());

                log.info("预加载秒杀商品成功 -> 秒杀商品序号: {}, 商品ID: {}, 商品名称: {}, 库存: {}",
                        seckillGoods.getGoodsId(),
                        seckillGoods.getId(),
                        seckillGoods.getName(),
                        seckillGoods.getStockCount());
            }

            log.info("========== 秒杀商品预加载完成，共加载 {} 个商品 ==========", seckillGoodsList.size());

        } catch (Exception e) {
            log.error("预加载秒杀商品到Redis失败", e);
            throw e;
        }
    }
}
