package io.github.turmony.seckillsystem.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.turmony.seckillsystem.entity.SeckillGoods;
import io.github.turmony.seckillsystem.vo.SeckillGoodsVO;

/**
 * 秒杀商品服务接口
 */
public interface SeckillGoodsService {

    /**
     * 分页查询秒杀商品列表（从Redis读取）
     */
    Page<SeckillGoodsVO> getSeckillGoodsList(Long current, Long size);

    /**
     * 根据商品ID查询秒杀商品详情（从Redis读取）
     */
    SeckillGoodsVO getSeckillGoodsByGoodsId(Long goodsId);

    /**
     * 添加秒杀商品
     */
    boolean addSeckillGoods(SeckillGoods seckillGoods);

    /**
     * 更新秒杀商品
     */
    boolean updateSeckillGoods(SeckillGoods seckillGoods);

    /**
     * 删除秒杀商品
     */
    boolean deleteSeckillGoods(Long id);
}
