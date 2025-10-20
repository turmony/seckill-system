package io.github.turmony.seckillsystem.service.Impl;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.turmony.seckillsystem.common.RedisKeyConstant;
import io.github.turmony.seckillsystem.entity.Goods;
import io.github.turmony.seckillsystem.entity.SeckillGoods;
import io.github.turmony.seckillsystem.mapper.GoodsMapper;
import io.github.turmony.seckillsystem.mapper.SeckillGoodsMapper;
import io.github.turmony.seckillsystem.service.SeckillGoodsService;
import io.github.turmony.seckillsystem.util.RedisUtil;
import io.github.turmony.seckillsystem.vo.SeckillGoodsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 秒杀商品服务实现类
 * 集成Redis缓存，优先从缓存读取数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillGoodsServiceImpl implements SeckillGoodsService {

    private final SeckillGoodsMapper seckillGoodsMapper;
    private final GoodsMapper goodsMapper;
    private final RedisUtil redisUtil;

    @Override
    public Page<SeckillGoodsVO> getSeckillGoodsList(Long current, Long size) {
        // 从数据库查询秒杀商品（这里也可以优化为从Redis读取）
        Page<SeckillGoods> page = new Page<>(current, size);
        Page<SeckillGoods> seckillGoodsPage = seckillGoodsMapper.selectPage(page,
                new QueryWrapper<SeckillGoods>()
                        .eq("status", 1)  // 只查询进行中的秒杀
                        .orderByDesc("create_time"));

        // 转换为VO，并从Redis获取库存信息
        Page<SeckillGoodsVO> voPage = new Page<>(seckillGoodsPage.getCurrent(),
                seckillGoodsPage.getSize(), seckillGoodsPage.getTotal());

        List<SeckillGoodsVO> voList = seckillGoodsPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public SeckillGoodsVO getSeckillGoodsByGoodsId(Long goodsId) {
        // 1. 先从Redis获取秒杀商品信息
        String goodsKey = RedisKeyConstant.getSeckillGoodsKey(goodsId);
        Object cacheObj = redisUtil.get(goodsKey);

        SeckillGoods seckillGoods;

        if (cacheObj != null) {
            // 从缓存中读取
            log.info("从Redis缓存读取秒杀商品信息，商品ID: {}", goodsId);
            seckillGoods = JSON.parseObject(JSON.toJSONString(cacheObj), SeckillGoods.class);
        } else {
            // 缓存未命中，从数据库查询
            log.info("缓存未命中，从数据库查询秒杀商品信息，商品ID: {}", goodsId);
            seckillGoods = seckillGoodsMapper.selectOne(
                    new QueryWrapper<SeckillGoods>().eq("goods_id", goodsId)
            );

            if (seckillGoods != null) {
                // 写入缓存
                redisUtil.set(goodsKey, seckillGoods);
                log.info("秒杀商品信息已写入Redis缓存，商品ID: {}", goodsId);
            }
        }

        if (seckillGoods == null) {
            return null;
        }

        // 2. 从Redis获取实时库存
        String stockKey = RedisKeyConstant.getSeckillStockKey(goodsId);
        Long stock = redisUtil.getLong(stockKey);
        if (stock != null) {
            seckillGoods.setStockCount(stock.intValue());
            log.info("从Redis读取实时库存，商品ID: {}, 库存: {}", goodsId, stock);
        }

        // 3. 转换为VO返回
        return convertToVO(seckillGoods);
    }

    @Override
    public boolean addSeckillGoods(SeckillGoods seckillGoods) {
        // 1. 插入数据库
        boolean result = seckillGoodsMapper.insert(seckillGoods) > 0;

        if (result) {
            // 2. 同步到Redis缓存
            String goodsKey = RedisKeyConstant.getSeckillGoodsKey(seckillGoods.getGoodsId());
            String stockKey = RedisKeyConstant.getSeckillStockKey(seckillGoods.getGoodsId());

            redisUtil.set(goodsKey, seckillGoods);
            redisUtil.set(stockKey, seckillGoods.getStockCount());

            log.info("新增秒杀商品并同步到Redis，商品ID: {}", seckillGoods.getGoodsId());
        }

        return result;
    }

    @Override
    public boolean updateSeckillGoods(SeckillGoods seckillGoods) {
        // 1. 更新数据库
        boolean result = seckillGoodsMapper.updateById(seckillGoods) > 0;

        if (result) {
            // 2. 更新Redis缓存
            String goodsKey = RedisKeyConstant.getSeckillGoodsKey(seckillGoods.getGoodsId());
            String stockKey = RedisKeyConstant.getSeckillStockKey(seckillGoods.getGoodsId());

            redisUtil.set(goodsKey, seckillGoods);
            redisUtil.set(stockKey, seckillGoods.getStockCount());

            log.info("更新秒杀商品并同步到Redis，商品ID: {}", seckillGoods.getGoodsId());
        }

        return result;
    }

    @Override
    public boolean deleteSeckillGoods(Long id) {
        // 1. 先查询商品信息
        SeckillGoods seckillGoods = seckillGoodsMapper.selectById(id);
        if (seckillGoods == null) {
            return false;
        }

        // 2. 删除数据库记录
        boolean result = seckillGoodsMapper.deleteById(id) > 0;

        if (result) {
            // 3. 删除Redis缓存
            String goodsKey = RedisKeyConstant.getSeckillGoodsKey(seckillGoods.getGoodsId());
            String stockKey = RedisKeyConstant.getSeckillStockKey(seckillGoods.getGoodsId());

            redisUtil.del(goodsKey, stockKey);

            log.info("删除秒杀商品并清除Redis缓存，商品ID: {}", seckillGoods.getGoodsId());
        }

        return result;
    }

    /**
     * 将SeckillGoods转换为SeckillGoodsVO
     * 包含关联的商品基本信息
     */
    private SeckillGoodsVO convertToVO(SeckillGoods seckillGoods) {
        SeckillGoodsVO vo = new SeckillGoodsVO();
        BeanUtils.copyProperties(seckillGoods, vo);

        // 查询关联的商品基本信息
        Goods goods = goodsMapper.selectById(seckillGoods.getGoodsId());
        if (goods != null) {
            vo.setGoodsName(goods.getName());
            vo.setGoodsTitle(goods.getTitle());
            vo.setGoodsImg(goods.getImg());
            vo.setGoodsDetail(goods.getDetail());
            vo.setGoodsPrice(goods.getPrice());
        }

        return vo;
    }
}
