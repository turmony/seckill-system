package io.github.turmony.seckillsystem.service.Impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.turmony.seckillsystem.entity.Goods;
import io.github.turmony.seckillsystem.mapper.GoodsMapper;
import io.github.turmony.seckillsystem.service.GoodsService;
import io.github.turmony.seckillsystem.vo.GoodsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 商品服务实现类
 */
@Service
@RequiredArgsConstructor
public class GoodsServiceImpl implements GoodsService {

    private final GoodsMapper goodsMapper;

    @Override
    public Page<GoodsVO> getGoodsList(Long current, Long size) {
        // 分页查询，只查询上架商品
        Page<Goods> page = new Page<>(current, size);
        Page<Goods> goodsPage = goodsMapper.selectPage(page,
                new QueryWrapper<Goods>()
                        .eq("status", 1)  // 只查询上架商品
                        .orderByDesc("create_time"));

        // 转换为VO
        Page<GoodsVO> voPage = new Page<>(goodsPage.getCurrent(),
                goodsPage.getSize(), goodsPage.getTotal());
        List<GoodsVO> voList = goodsPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public GoodsVO getGoodsById(Long id) {
        Goods goods = goodsMapper.selectById(id);
        return goods == null ? null : convertToVO(goods);
    }

    @Override
    public boolean addGoods(Goods goods) {
        // 新增商品时，默认设置为上架状态，销量为0
        if (goods.getStatus() == null) {
            goods.setStatus(1);
        }
        if (goods.getSales() == null) {
            goods.setSales(0);
        }
        return goodsMapper.insert(goods) > 0;
    }

    @Override
    public boolean updateGoods(Goods goods) {
        return goodsMapper.updateById(goods) > 0;
    }

    @Override
    public boolean deleteGoods(Long id) {
        return goodsMapper.deleteById(id) > 0;
    }

    /**
     * 实体转VO
     */
    private GoodsVO convertToVO(Goods goods) {
        GoodsVO vo = new GoodsVO();
        BeanUtils.copyProperties(goods, vo);
        return vo;
    }
}
