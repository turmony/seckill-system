package io.github.turmony.seckillsystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seckill_order")
public class SeckillOrder {

    /**
     * 主键ID
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
     * 订单号
     */
    private String orderNo;

    /**
     * 秒杀商品ID
     */
    private Long seckillGoodsId;

    /**
     * 订单ID（支付系统用）
     */
    private String orderId;

    /**
     * 商品名称（冗余字段）
     */
    private String goodsName;

    /**
     * 秒杀价格（冗余字段）
     */
    private BigDecimal seckillPrice;

    /**
     * 订单状态：0-待支付，1-已支付，2-已取消
     */
    private Integer status;

    /**
     * 秒杀状态：0-秒杀中，1-秒杀成功，2-秒杀失败
     */
    private Integer seckillStatus;

    /**
     * 支付时间
     */
    private LocalDateTime payTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}