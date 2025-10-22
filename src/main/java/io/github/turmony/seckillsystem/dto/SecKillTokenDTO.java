package io.github.turmony.seckillsystem.dto;

import lombok.Data;

/**
 * 秒杀令牌DTO
 */
@Data
public class SecKillTokenDTO {
    /**
     * 秒杀令牌
     */
    private String token;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 过期时间（秒）
     */
    private Long expireSeconds;

    /**
     * 生成时间戳
     */
    private Long timestamp;
}
