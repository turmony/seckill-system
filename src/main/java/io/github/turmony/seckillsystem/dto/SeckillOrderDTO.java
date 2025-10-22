package io.github.turmony.seckillsystem.dto;

import lombok.Data;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 秒杀下单请求DTO
 */
@Data
public class SeckillOrderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品ID
     */
    @NotNull(message = "商品ID不能为空")
    private Long goodsId;

    /**
     * 秒杀令牌（一次性Token，防止重复提交）
     */
    @NotBlank(message = "秒杀令牌不能为空")
    private String token;
}