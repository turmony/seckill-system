package io.github.turmony.seckillsystem.service;

import io.github.turmony.seckillsystem.dto.SeckillOrderDTO;
import io.github.turmony.seckillsystem.vo.SeckillOrderVO;

import java.util.List;

/**
 * 秒杀订单服务接口
 */
public interface SeckillOrderService {


    /**
     * 查询用户在指定商品的订单
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 订单信息
     */
    SeckillOrderVO getOrderByUserIdAndGoodsId(Long userId, Long goodsId);

    /**
     * 执行秒杀（异步模式，Step 14）
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 订单ID（用于后续查询）
     */
    String doSeckill(Long userId, Long goodsId);

    /**
     * 处理秒杀订单（MQ消费者调用，Step 14）
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @param orderId 订单ID
     */
    void processOrder(Long userId, Long goodsId, String orderId);

    /**
     * 根据订单ID查询订单（Step 14）
     * @param orderId 订单ID
     * @return 订单信息
     */
    SeckillOrderVO getOrderByOrderId(String orderId);

    /**
     * ✅ Step 15: 查询用户的订单列表
     * @param userId 用户ID
     * @return 订单列表
     */
    List<SeckillOrderVO> getUserOrders(Long userId);

    /**
     * ✅ Step 15: 根据状态查询用户的订单列表
     * @param userId 用户ID
     * @param status 订单状态：0-排队中 1-成功 2-失败 null-全部
     * @return 订单列表
     */
    List<SeckillOrderVO> getUserOrdersByStatus(Long userId, Integer status);

    /**
     * ✅ Step 15: 查询订单详情（根据订单ID）
     * @param userId 用户ID
     * @param orderId 订单ID
     * @return 订单详情
     */
    SeckillOrderVO getOrderDetail(Long userId, String orderId);
}