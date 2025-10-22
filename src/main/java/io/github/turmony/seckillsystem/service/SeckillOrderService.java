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

    /**
     * 执行秒杀（异步模式 - Step 14改造）
     *
     * 此方法在令牌验证通过后调用，执行异步秒杀流程：
     * 1. 时间校验
     * 2. Lua脚本扣减Redis库存
     * 3. 发送MQ消息
     * 4. 立即返回"排队中"状态
     *
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 订单ID（用于后续查询订单状态）
     */
    String doSeckill(Long userId, Long goodsId);

    /**
     * 处理秒杀订单（MQ消费者调用）
     *
     * 此方法由MQ消费者异步调用，完成订单的最终处理：
     * 1. 使用Redisson分布式锁（防止重复消费）
     * 2. 扣减MySQL库存
     * 3. 更新订单状态为成功或失败
     *
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @param orderId 订单ID
     */
    void processOrder(Long userId, Long goodsId, String orderId);

    /**
     * 根据订单ID查询订单
     *
     * @param orderId 订单ID
     * @return 订单信息
     */
    SeckillOrderVO getOrderByOrderId(String orderId);
}