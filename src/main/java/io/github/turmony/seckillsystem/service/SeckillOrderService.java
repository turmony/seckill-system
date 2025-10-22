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
     * 执行秒杀（集成令牌验证后的统一入口）
     *
     * 此方法在令牌验证通过后调用，执行完整的秒杀流程：
     * 1. 时间校验
     * 2. Lua脚本扣减Redis库存
     * 3. Redisson分布式锁
     * 4. 创建订单
     *
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @return 订单ID（用于后续查询订单状态）
     */
    String doSeckill(Long userId, Long goodsId);
}