package io.github.turmony.seckillsystem.dto;


import lombok.Data;
import jakarta.validation.constraints.NotNull;
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
}
