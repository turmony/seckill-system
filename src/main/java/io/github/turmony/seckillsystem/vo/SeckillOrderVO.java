package io.github.turmony.seckillsystem.vo;


import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单VO
 */
@Data
public class SeckillOrderVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 商品ID
     */
    private Long goodsId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;

    /**
     * 订单状态：0-待支付 1-已支付 2-已取消
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
