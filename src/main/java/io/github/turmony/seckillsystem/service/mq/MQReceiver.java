package io.github.turmony.seckillsystem.service.mq;

import com.rabbitmq.client.Channel;
import io.github.turmony.seckillsystem.common.MQConstant;
import io.github.turmony.seckillsystem.dto.SeckillMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * MQ消息接收者（消费者）
 * 监听RabbitMQ队列，处理秒杀订单
 */
@Slf4j
@Service
public class MQReceiver {

    // TODO: 注入订单服务，在下一步(Step 14)会使用
    // @Autowired
    // private SeckillOrderService seckillOrderService;

    /**
     * 监听秒杀订单队列
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
            log.info("接收到秒杀消息: userId={}, goodsId={}, orderId={}",
                    message.getUserId(), message.getGoodsId(), message.getOrderId());

            // 验证消息完整性
            if (message.getUserId() == null || message.getGoodsId() == null) {
                log.error("秒杀消息数据不完整: {}", message);
                // 拒绝消息，不重新入队（因为数据有问题，重试也没用）
                channel.basicNack(deliveryTag, false, false);
                return;
            }

            // ==============================================
            // TODO: Step 14 会实现这部分逻辑
            // ==============================================
            // 1. 使用Redisson分布式锁（防止重复消费）
            // 2. 扣减MySQL库存
            // 3. 创建订单记录
            // 4. 记录操作日志
            // ==============================================

            // 临时模拟处理（Step 14会替换）
            log.info("处理秒杀订单中... orderId={}", message.getOrderId());

            // 模拟业务处理耗时
            Thread.sleep(100);

            log.info("秒杀订单处理成功: orderId={}", message.getOrderId());

            // 手动确认消息（ACK）
            channel.basicAck(deliveryTag, false);

        } catch (InterruptedException e) {
            log.error("秒杀订单处理被中断: orderId={}", message.getOrderId(), e);
            Thread.currentThread().interrupt();
            try {
                // 拒绝消息并重新入队
                channel.basicNack(deliveryTag, false, true);
            } catch (Exception ex) {
                log.error("消息NACK失败", ex);
            }

        } catch (Exception e) {
            log.error("秒杀订单处理失败: userId={}, goodsId={}, orderId={}, error={}",
                    message.getUserId(), message.getGoodsId(), message.getOrderId(),
                    e.getMessage(), e);

            try {
                // 判断是否重试（可根据重试次数决定）
                // 这里简单处理：业务异常不重试，直接进入死信队列
                channel.basicNack(deliveryTag, false, false);

                // 也可以选择重新入队重试（最多3次）
                // channel.basicNack(deliveryTag, false, true);

            } catch (Exception ex) {
                log.error("消息NACK失败", ex);
            }
        }
    }

    /**
     * 监听死信队列（可选）
     * 处理消费失败的消息，可以记录日志、发送告警等
     */
    @RabbitListener(queues = MQConstant.SECKILL_DEAD_QUEUE)
    public void receiveDeadMessage(@Payload SeckillMessageDTO message) {
        log.error("接收到死信消息（消费失败）: userId={}, goodsId={}, orderId={}",
                message.getUserId(), message.getGoodsId(), message.getOrderId());

        // TODO: 可以在这里实现
        // 1. 记录到失败日志表
        // 2. 发送告警通知
        // 3. 人工介入处理
    }
}