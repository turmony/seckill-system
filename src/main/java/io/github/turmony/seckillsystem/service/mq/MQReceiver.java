package io.github.turmony.seckillsystem.service.mq;

import com.rabbitmq.client.Channel;
import io.github.turmony.seckillsystem.common.MQConstant;
import io.github.turmony.seckillsystem.dto.SeckillMessageDTO;
import io.github.turmony.seckillsystem.service.SeckillOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * MQ消息接收者（消费者）
 * Step 14改造：实现真正的秒杀订单异步处理逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MQReceiver {

    private final SeckillOrderService seckillOrderService;

    /**
     * 监听秒杀订单队列
     *
     * 完整处理流程：
     * 1. 接收并验证消息
     * 2. 调用订单服务处理订单（分布式锁 + 扣减MySQL库存 + 更新订单状态）
     * 3. 手动确认消息（ACK）
     *
     * 异常处理：
     * - 数据不完整：拒绝消息，不重新入队
     * - 业务异常：拒绝消息，不重新入队（已在服务层处理）
     * - 系统异常：可选择重新入队重试
     *
     * @param message 秒杀消息对象
     * @param channel RabbitMQ通道，用于手动确认
     * @param deliveryTag 消息投递标签
     */
    @RabbitListener(queues = MQConstant.SECKILL_QUEUE)
    public void receiveSeckillMessage(
            @Payload SeckillMessageDTO message,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        try {
            log.info("📨 接收到秒杀消息: userId={}, goodsId={}, orderId={}",
                    message.getUserId(), message.getGoodsId(), message.getOrderId());

            // ============ Step 1: 验证消息完整性 ============
            if (message.getUserId() == null || message.getGoodsId() == null || message.getOrderId() == null) {
                log.error("❌ 秒杀消息数据不完整: {}", message);
                // 拒绝消息，不重新入队（因为数据有问题，重试也没用）
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            log.info("✅ 消息完整性验证通过");

            // ============ Step 2: 调用订单服务处理订单 ============
            // 此方法包含：
            // 1. Redisson分布式锁（防止重复消费）
            // 2. 扣减MySQL库存
            // 3. 更新订单状态为成功或失败
            // 4. 异常情况回滚Redis库存
            long startTime = System.currentTimeMillis();

            seckillOrderService.processOrder(
                    message.getUserId(),
                    message.getGoodsId(),
                    message.getOrderId()
            );

            long costTime = System.currentTimeMillis() - startTime;

            log.info("✅ 秒杀订单处理成功: orderId={}, 耗时: {}ms",
                    message.getOrderId(), costTime);

            // ============ Step 3: 手动确认消息（ACK） ============
            channel.basicAck(deliveryTag, false);

            log.info("📮 消息已确认: orderId={}", message.getOrderId());

        } catch (Exception e) {
            log.error("❌ 秒杀订单处理失败: userId={}, goodsId={}, orderId={}, error={}",
                    message.getUserId(), message.getGoodsId(), message.getOrderId(),
                    e.getMessage(), e);

            try {
                // ============ 异常处理策略 ============
                // 策略1: 不重试，直接拒绝（推荐）
                // 因为订单处理失败已经在服务层更新了订单状态为失败
                // 重试可能导致重复处理
                channel.basicNack(deliveryTag, false, false);

                log.warn("消息已拒绝（不重新入队）: orderId={}", message.getOrderId());

                // 策略2: 重新入队重试（可选，需要注意重试次数）
                // 可以通过消息头的x-death来判断重试次数
                // channel.basicNack(deliveryTag, false, true);

            } catch (Exception ex) {
                log.error("消息NACK失败", ex);
            }
        }
    }

    /**
     * 监听死信队列（可选）
     * 处理消费失败的消息，可以记录日志、发送告警等
     *
     * 死信队列的消息来源：
     * 1. 消息被拒绝（basic.reject 或 basic.nack），且 requeue=false
     * 2. 消息过期（TTL）
     * 3. 队列达到最大长度
     */
    @RabbitListener(queues = MQConstant.SECKILL_DEAD_QUEUE)
    public void receiveDeadMessage(@Payload SeckillMessageDTO message) {
        log.error("💀 接收到死信消息（消费失败）: userId={}, goodsId={}, orderId={}",
                message.getUserId(), message.getGoodsId(), message.getOrderId());

        // ============ 死信处理建议 ============
        // 1. 记录到失败日志表（用于人工介入处理）
        // 2. 发送告警通知（邮件、短信、钉钉等）
        // 3. 更新订单状态为"处理失败-待人工审核"
        // 4. 统计失败原因，用于系统优化

        // TODO: 实现具体的死信处理逻辑
        // 示例：
        // - operationLogService.saveFailedOrderLog(message);
        // - alertService.sendAlert("秒杀订单处理失败", message);
        // - seckillOrderService.markOrderAsFailed(message.getOrderId());

        log.error("⚠️ 请人工检查该订单: orderId={}, userId={}, goodsId={}",
                message.getOrderId(), message.getUserId(), message.getGoodsId());
    }
}