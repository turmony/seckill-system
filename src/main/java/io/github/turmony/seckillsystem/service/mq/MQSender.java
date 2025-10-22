package io.github.turmony.seckillsystem.service.mq;

import io.github.turmony.seckillsystem.common.MQConstant;
import io.github.turmony.seckillsystem.dto.SeckillMessageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * MQ消息发送者（生产者）
 * 负责将秒杀消息发送到RabbitMQ队列
 */
@Slf4j
@Service
public class MQSender {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 发送秒杀消息到队列
     *
     * @param message 秒杀消息对象
     */
    public void sendSeckillMessage(SeckillMessageDTO message) {
        try {
            log.info("发送秒杀消息到MQ: userId={}, goodsId={}, orderId={}",
                    message.getUserId(), message.getGoodsId(), message.getOrderId());

            // 发送消息到指定交换机和路由键
            rabbitTemplate.convertAndSend(
                    MQConstant.SECKILL_EXCHANGE,
                    MQConstant.SECKILL_ROUTING_KEY,
                    message
            );

            log.info("秒杀消息发送成功: orderId={}", message.getOrderId());

        } catch (Exception e) {
            log.error("秒杀消息发送失败: userId={}, goodsId={}, error={}",
                    message.getUserId(), message.getGoodsId(), e.getMessage(), e);
            // 可以在这里实现补偿机制，比如重试或记录到数据库
            throw new RuntimeException("消息发送失败", e);
        }
    }

    /**
     * 批量发送秒杀消息（可选）
     *
     * @param messages 消息列表
     */
    public void sendBatchSeckillMessage(java.util.List<SeckillMessageDTO> messages) {
        if (messages == null || messages.isEmpty()) {
            log.warn("批量发送消息列表为空");
            return;
        }

        log.info("开始批量发送秒杀消息，数量: {}", messages.size());

        for (SeckillMessageDTO message : messages) {
            try {
                sendSeckillMessage(message);
            } catch (Exception e) {
                log.error("批量发送中的单条消息失败: orderId={}", message.getOrderId(), e);
                // 继续发送下一条，不中断
            }
        }

        log.info("批量发送秒杀消息完成");
    }
}