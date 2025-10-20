package io.github.turmony.seckillsystem.service;


import io.github.turmony.seckillsystem.dto.SeckillOrderDTO;
import io.github.turmony.seckillsystem.vo.SeckillOrderVO;

/**
 * 秒杀订单服务接口
 */
public interface SeckillOrderService {

    /**
     * 秒杀下单（基础版）
     * @param userId 用户ID
     * @param orderDTO 下单请求参数
     * @return 订单信息
     */
    SeckillOrderVO createOrder(Long userId, SeckillOrderDTO orderDTO);

    /**
     * 查询用户订单
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 订单信息
     */
    SeckillOrderVO getOrderByUserIdAndGoodsId(Long userId, Long goodsId);
}
