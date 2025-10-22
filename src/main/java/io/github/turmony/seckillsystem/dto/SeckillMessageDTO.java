package io.github.turmony.seckillsystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀消息传输对象
 * 用于RabbitMQ消息传递
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillMessageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 订单ID（可选，可以在消费端生成）
     */
    private String orderId;

    /**
     * 秒杀令牌（用于验证）
     */
    private String token;

    /**
     * 消息创建时间戳
     */
    private Long timestamp;
}