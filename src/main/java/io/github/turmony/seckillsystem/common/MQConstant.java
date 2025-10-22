package io.github.turmony.seckillsystem.common;

/**
 * MQ常量类
 * 定义队列名称、交换机名称、路由键等
 */
public class MQConstant {

    /**
     * 秒杀订单队列名称
     */
    public static final String SECKILL_QUEUE = "seckill.order.queue";

    /**
     * 秒杀交换机名称
     */
    public static final String SECKILL_EXCHANGE = "seckill.order.exchange";

    /**
     * 秒杀路由键
     */
    public static final String SECKILL_ROUTING_KEY = "seckill.order";

    /**
     * 死信队列名称（可选，用于处理失败消息）
     */
    public static final String SECKILL_DEAD_QUEUE = "seckill.order.dead.queue";

    /**
     * 死信交换机名称（可选）
     */
    public static final String SECKILL_DEAD_EXCHANGE = "seckill.order.dead.exchange";

    /**
     * 死信路由键（可选）
     */
    public static final String SECKILL_DEAD_ROUTING_KEY = "seckill.order.dead";
}