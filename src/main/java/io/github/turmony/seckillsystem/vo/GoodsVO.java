package io.github.turmony.seckillsystem.vo;


import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 商品视图对象
 */
@Data
public class GoodsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 商品ID
     */
    private Long id;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品标题
     */
    private String title;

    /**
     * 商品图片
     */
    private String img;

    /**
     * 商品详情
     */
    private String detail;

    /**
     * 商品价格
     */
    private BigDecimal price;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * 销量
     */
    private Integer sales;

    /**
     * 商品状态：0-下架 1-上架
     */
    private Integer status;
}
