package io.github.turmony.seckillsystem.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.turmony.seckillsystem.common.RedisKeyConstant;
import io.github.turmony.seckillsystem.dto.SeckillOrderDTO;
import io.github.turmony.seckillsystem.entity.Goods;
import io.github.turmony.seckillsystem.entity.SeckillGoods;
import io.github.turmony.seckillsystem.entity.SeckillOrder;
import io.github.turmony.seckillsystem.mapper.GoodsMapper;
import io.github.turmony.seckillsystem.mapper.SeckillGoodsMapper;
import io.github.turmony.seckillsystem.mapper.SeckillOrderMapper;
import io.github.turmony.seckillsystem.service.SeckillOrderService;
import io.github.turmony.seckillsystem.util.RedisUtil;
import io.github.turmony.seckillsystem.vo.SeckillOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

/**
 * 秒杀订单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final GoodsMapper goodsMapper;
    private final RedisUtil redisUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrderVO createOrder(Long userId, SeckillOrderDTO orderDTO) {
        Long goodsId = orderDTO.getGoodsId();

        log.info("开始秒杀下单，用户ID: {}, 商品ID: {}", userId, goodsId);

        // 1. 查询秒杀商品信息（从数据库查询，确保数据准确）
        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                new QueryWrapper<SeckillGoods>().eq("goods_id", goodsId)
        );

        if (seckillGoods == null) {
            log.warn("秒杀商品不存在，商品ID: {}", goodsId);
            throw new RuntimeException("秒杀商品不存在");
        }

        // 2. 校验秒杀时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckillGoods.getStartTime())) {
            log.warn("秒杀未开始，商品ID: {}, 开始时间: {}", goodsId, seckillGoods.getStartTime());
            throw new RuntimeException("秒杀活动尚未开始");
        }
        if (now.isAfter(seckillGoods.getEndTime())) {
            log.warn("秒杀已结束，商品ID: {}, 结束时间: {}", goodsId, seckillGoods.getEndTime());
            throw new RuntimeException("秒杀活动已结束");
        }

        // 3. 检查是否已经购买过（防止重复下单）
        SeckillOrder existOrder = seckillOrderMapper.selectOne(
                new QueryWrapper<SeckillOrder>()
                        .eq("user_id", userId)
                        .eq("goods_id", goodsId)
        );

        if (existOrder != null) {
            log.warn("用户已购买过该商品，用户ID: {}, 商品ID: {}", userId, goodsId);
            throw new RuntimeException("您已经购买过该商品了");
        }

        // 4. 检查库存（从Redis读取，提高性能）
        String stockKey = RedisKeyConstant.getSeckillStockKey(goodsId);
        Long stock = redisUtil.getLong(stockKey);

        if (stock == null || stock <= 0) {
            log.warn("库存不足，商品ID: {}, Redis库存: {}", goodsId, stock);
            throw new RuntimeException("商品已售罄");
        }

        // 5. 扣减MySQL库存（乐观锁，防止超卖）
        int updateCount = seckillGoodsMapper.update(null,
                new UpdateWrapper<SeckillGoods>()
                        .setSql("stock_count = stock_count - 1")
                        .eq("goods_id", goodsId)
                        .gt("stock_count", 0)  // 库存必须大于0
        );

        if (updateCount == 0) {
            log.warn("库存扣减失败（数据库库存不足），商品ID: {}", goodsId);
            throw new RuntimeException("商品已售罄");
        }

        log.info("数据库库存扣减成功，商品ID: {}", goodsId);

        // 6. 同步扣减Redis库存
        redisUtil.decr(stockKey);
        log.info("Redis库存扣减成功，商品ID: {}, 剩余库存: {}", goodsId, redisUtil.getLong(stockKey));

        // 7. 查询商品基本信息（用于冗余字段）
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            log.error("商品不存在，商品ID: {}", goodsId);
            throw new RuntimeException("商品信息异常");
        }

        // 8. 创建订单
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setGoodsId(goodsId);

        // ⭐ 设置秒杀商品ID（这是之前缺失的关键字段）
        order.setSeckillGoodsId(seckillGoods.getId());

        // 设置订单号
        order.setOrderNo(generateOrderNo());

        // ⭐ 设置订单ID（支付系统用）
        order.setOrderId(generateOrderId());

        // ⭐ 冗余字段：商品名称（防止商品被删除后无法显示）
        order.setGoodsName(goods.getName());

        // ⭐ 冗余字段：秒杀价格（记录当时的成交价格）
        order.setSeckillPrice(seckillGoods.getSeckillPrice());

        // 订单状态
        order.setStatus(0);  // 0-待支付

        // ⭐ 秒杀状态（如果表中有这个字段）
        order.setSeckillStatus(0);  // 0-秒杀中

        // 创建时间
        order.setCreateTime(LocalDateTime.now());

        int insertCount = seckillOrderMapper.insert(order);

        if (insertCount == 0) {
            log.error("订单创建失败，用户ID: {}, 商品ID: {}", userId, goodsId);
            throw new RuntimeException("订单创建失败");
        }

        log.info("订单创建成功，订单号: {}, 订单ID: {}, 用户ID: {}, 商品ID: {}, 秒杀商品ID: {}",
                order.getOrderNo(), order.getOrderId(), userId, goodsId, seckillGoods.getId());

        // 9. 转换为VO返回
        return convertToVO(order, seckillGoods);
    }

    @Override
    public SeckillOrderVO getOrderByUserIdAndGoodsId(Long userId, Long goodsId) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new QueryWrapper<SeckillOrder>()
                        .eq("user_id", userId)
                        .eq("goods_id", goodsId)
        );

        if (order == null) {
            return null;
        }

        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                new QueryWrapper<SeckillGoods>().eq("goods_id", goodsId)
        );

        return convertToVO(order, seckillGoods);
    }

    /**
     * 生成订单号
     * 格式：yyyyMMddHHmmss + 6位随机数
     * 示例：20251020214629123456
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%06d", new Random().nextInt(1000000));
        return timestamp + random;
    }

    /**
     * ⭐ 生成订单ID（用于支付系统）
     * 使用UUID去掉横线，保证全局唯一
     */
    private String generateOrderId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 转换为VO
     */
    private SeckillOrderVO convertToVO(SeckillOrder order, SeckillGoods seckillGoods) {
        SeckillOrderVO vo = new SeckillOrderVO();
        BeanUtils.copyProperties(order, vo);

        if (seckillGoods != null) {
            vo.setSeckillPrice(seckillGoods.getSeckillPrice());

            // 查询商品基本信息
            Goods goods = goodsMapper.selectById(seckillGoods.getGoodsId());
            if (goods != null) {
                vo.setGoodsName(goods.getName());
                vo.setGoodsImg(goods.getImg());
            }
        }

        return vo;
    }
}