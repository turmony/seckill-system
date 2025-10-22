package io.github.turmony.seckillsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀订单实体类
 */
@Data
@TableName("seckill_order")
public class SeckillOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID（数据库主键）
     */
    @TableId(type = IdType.AUTO)
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
     * 秒杀商品ID
     */
    private Long seckillGoodsId;

    /**
     * 订单号（格式：yyyyMMddHHmmss + 6位随机数）
     * 示例：20251020214629123456
     */
    private String orderNo;

    /**
     * 订单唯一ID（UUID格式，用于支付系统）
     * 示例：e4f5g6h7i8j9k0l1m2n3o4p5
     */
    private String orderId;

    /**
     * 商品名称（冗余字段，提高查询效率）
     */
    private String goodsName;

    /**
     * 秒杀价格（冗余字段）
     */
    private BigDecimal seckillPrice;

    /**
     * 订单状态：0-排队中 1-成功 2-失败
     */
    private Integer status;

    /**
     * 秒杀状态：0-秒杀中 1-秒杀成功 2-秒杀失败
     */
    private Integer seckillStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}