package io.github.turmony.seckillsystem.controller;

import io.github.turmony.seckillsystem.common.RedisKeyConstant;
import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.entity.SeckillGoods;
import io.github.turmony.seckillsystem.mapper.SeckillGoodsMapper;
import io.github.turmony.seckillsystem.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 秒杀商品管理控制器
 * 提供管理端功能，如手动预热、清除缓存等
 */
@Slf4j
@RestController
@RequestMapping("/admin/seckill")
@RequiredArgsConstructor
public class SeckillGoodsAdminController {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisUtil redisUtil;

    /**
     * 手动触发预热秒杀商品到Redis
     * @return 预热结果
     */
    @PostMapping("/preload")
    public Result<Map<String, Object>> preloadSeckillGoods() {
        log.info("手动触发秒杀商品预热");

        try {
            // 1. 查询所有秒杀商品
            List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);

            if (seckillGoodsList == null || seckillGoodsList.isEmpty()) {
                return Result.error("没有需要预热的秒杀商品");
            }

            // 2. 批量预热
            int successCount = 0;
            int failCount = 0;

            for (SeckillGoods seckillGoods : seckillGoodsList) {
                try {
                    String goodsKey = RedisKeyConstant.getSeckillGoodsKey(seckillGoods.getGoodsId());
                    String stockKey = RedisKeyConstant.getSeckillStockKey(seckillGoods.getGoodsId());

                    redisUtil.set(goodsKey, seckillGoods);
                    redisUtil.set(stockKey, seckillGoods.getStockCount());

                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.error("预热失败，商品ID: {}, 错误: {}", seckillGoods.getGoodsId(), e.getMessage());
                }
            }

            // 3. 返回预热结果
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("totalCount", seckillGoodsList.size());
            resultMap.put("successCount", successCount);
            resultMap.put("failCount", failCount);

            log.info("预热完成: 总数={}, 成功={}, 失败={}",
                    seckillGoodsList.size(), successCount, failCount);

            return Result.success(resultMap);

        } catch (Exception e) {
            log.error("预热过程发生异常: {}", e.getMessage(), e);
            return Result.error("预热失败: " + e.getMessage());
        }
    }

    /**
     * 清除所有秒杀商品缓存
     * @return 操作结果
     */
    @DeleteMapping("/cache/clear")
    public Result<String> clearSeckillCache() {
        log.info("手动清除秒杀商品缓存");

        try {
            List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);

            if (seckillGoodsList == null || seckillGoodsList.isEmpty()) {
                return Result.success("没有需要清除的缓存");
            }

            for (SeckillGoods goods : seckillGoodsList) {
                String goodsKey = RedisKeyConstant.getSeckillGoodsKey(goods.getGoodsId());
                String stockKey = RedisKeyConstant.getSeckillStockKey(goods.getGoodsId());

                redisUtil.del(goodsKey, stockKey);
            }

            log.info("成功清除 {} 个秒杀商品的缓存", seckillGoodsList.size());
            return Result.success("缓存清除成功，共清除 " + seckillGoodsList.size() + " 个商品缓存");

        } catch (Exception e) {
            log.error("清除缓存失败: {}", e.getMessage(), e);
            return Result.error("清除缓存失败: " + e.getMessage());
        }
    }

    /**
     * 查看Redis中的库存数据
     * @return 所有商品的库存信息
     */
    @GetMapping("/cache/stock")
    public Result<Map<Long, Long>> getStockFromRedis() {
        log.info("查询Redis中的库存数据");

        try {
            List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(null);
            Map<Long, Long> stockMap = new HashMap<>();

            for (SeckillGoods goods : seckillGoodsList) {
                String stockKey = RedisKeyConstant.getSeckillStockKey(goods.getGoodsId());
                Long stock = redisUtil.getLong(stockKey);
                stockMap.put(goods.getGoodsId(), stock != null ? stock : 0L);
            }

            return Result.success(stockMap);

        } catch (Exception e) {
            log.error("查询库存失败: {}", e.getMessage(), e);
            return Result.error("查询库存失败: " + e.getMessage());
        }
    }

    /**
     * 重置指定商品的库存到初始值
     * @param goodsId 商品ID
     * @return 操作结果
     */
    @PutMapping("/stock/reset/{goodsId}")
    public Result<String> resetStock(@PathVariable Long goodsId) {
        log.info("重置商品库存，商品ID: {}", goodsId);

        try {
            // 从数据库获取初始库存
            SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SeckillGoods>()
                            .eq("goods_id", goodsId)
            );

            if (seckillGoods == null) {
                return Result.error("秒杀商品不存在");
            }

            // 重置Redis中的库存
            String stockKey = RedisKeyConstant.getSeckillStockKey(goodsId);
            redisUtil.set(stockKey, seckillGoods.getStockCount());

            log.info("库存重置成功，商品ID: {}, 库存: {}", goodsId, seckillGoods.getStockCount());
            return Result.success("库存重置成功，当前库存: " + seckillGoods.getStockCount());

        } catch (Exception e) {
            log.error("重置库存失败: {}", e.getMessage(), e);
            return Result.error("重置库存失败: " + e.getMessage());
        }
    }
}