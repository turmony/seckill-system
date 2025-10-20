package io.github.turmony.seckillsystem.config;

import io.github.turmony.seckillsystem.common.RedisKeyConstant;
import io.github.turmony.seckillsystem.entity.SeckillGoods;
import io.github.turmony.seckillsystem.mapper.SeckillGoodsMapper;
import io.github.turmony.seckillsystem.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀商品初始化器
 * 项目启动时自动将秒杀商品信息和库存预热到Redis
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillGoodsInitializer implements CommandLineRunner {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisUtil redisUtil;

    @Override
    public void run(String... args) throws Exception {
        log.info("=================开始预热秒杀商品数据到Redis=================");

        try {
            // 1. 从数据库查询所有秒杀商品
            List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);

            if (seckillGoodsList == null || seckillGoodsList.isEmpty()) {
                log.warn("数据库中没有秒杀商品数据，跳过预热");
                return;
            }

            log.info("查询到 {} 个秒杀商品，开始预热到Redis", seckillGoodsList.size());

            // 2. 批量预热到Redis
            int successCount = 0;
            for (SeckillGoods seckillGoods : seckillGoodsList) {
                try {
                    // 预热商品详情信息
                    String goodsKey = RedisKeyConstant.getSeckillGoodsKey(seckillGoods.getGoodsId());
                    redisUtil.set(goodsKey, seckillGoods);

                    // 预热库存信息（使用String类型存储）
                    String stockKey = RedisKeyConstant.getSeckillStockKey(seckillGoods.getGoodsId());
                    redisUtil.set(stockKey, seckillGoods.getStockCount());

                    successCount++;
                    log.info("预热成功 -> 商品ID: {}, 商品名称: {}, 库存: {}",
                            seckillGoods.getGoodsId(),
                            seckillGoods.getGoodsId(),
                            seckillGoods.getStockCount());

                } catch (Exception e) {
                    log.error("预热失败 -> 商品ID: {}, 错误信息: {}",
                            seckillGoods.getGoodsId(), e.getMessage());
                }
            }

            log.info("=================秒杀商品数据预热完成=================");
            log.info("预热结果: 总数={}, 成功={}, 失败={}",
                    seckillGoodsList.size(), successCount, seckillGoodsList.size() - successCount);

            // 3. 验证预热结果（可选）
            verifyPreloadData(seckillGoodsList);

        } catch (Exception e) {
            log.error("秒杀商品数据预热过程发生异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 验证预热数据是否成功
     */
    private void verifyPreloadData(List<SeckillGoods> seckillGoodsList) {
        log.info("开始验证Redis预热数据...");

        int verifySuccessCount = 0;
        for (SeckillGoods goods : seckillGoodsList) {
            String stockKey = RedisKeyConstant.getSeckillStockKey(goods.getGoodsId());
            Long stock = redisUtil.getLong(stockKey);

            if (stock != null && stock.equals(goods.getStockCount().longValue())) {
                verifySuccessCount++;
            } else {
                log.warn("验证失败 -> 商品ID: {}, 预期库存: {}, 实际库存: {}",
                        goods.getGoodsId(), goods.getStockCount(), stock);
            }
        }

        log.info("验证完成: 成功={}/{}", verifySuccessCount, seckillGoodsList.size());
    }
}