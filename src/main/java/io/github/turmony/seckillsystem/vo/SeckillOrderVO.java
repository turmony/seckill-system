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
     * 订单ID（数据库主键）
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
     * 订单号（格式：yyyyMMddHHmmss + 6位随机数）
     * 示例：20251020214629123456
     */
    private String orderNo;

    /**
     * ✅ 新增：订单唯一ID（UUID格式，用于支付系统）
     * 示例：e4f5g6h7i8j9k0l1m2n3o4p5
     */
    private String orderId;

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