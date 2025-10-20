package io.github.turmony.seckillsystem.service;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.turmony.seckillsystem.entity.Goods;
import io.github.turmony.seckillsystem.vo.GoodsVO;

/**
 * 商品服务接口
 */
public interface GoodsService {

    /**
     * 分页查询商品列表
     * @param current 当前页
     * @param size 每页大小
     * @return 商品分页数据
     */
    Page<GoodsVO> getGoodsList(Long current, Long size);

    /**
     * 根据ID查询商品详情
     * @param id 商品ID
     * @return 商品详情
     */
    GoodsVO getGoodsById(Long id);

    /**
     * 添加商品
     * @param goods 商品信息
     * @return 是否成功
     */
    boolean addGoods(Goods goods);

    /**
     * 更新商品
     * @param goods 商品信息
     * @return 是否成功
     */
    boolean updateGoods(Goods goods);

    /**
     * 删除商品
     * @param id 商品ID
     * @return 是否成功
     */
    boolean deleteGoods(Long id);
}
