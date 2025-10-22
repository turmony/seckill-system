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
import io.github.turmony.seckillsystem.util.LuaScriptUtil;
import io.github.turmony.seckillsystem.util.RedisUtil;
import io.github.turmony.seckillsystem.util.RedissonLockUtil;
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
 * 集成Redisson分布式锁，防止同一用户重复下单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final GoodsMapper goodsMapper;
    private final RedisUtil redisUtil;
    private final LuaScriptUtil luaScriptUtil;
    private final RedissonLockUtil redissonLockUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SeckillOrderVO createOrder(Long userId, SeckillOrderDTO orderDTO) {
        Long goodsId = orderDTO.getGoodsId();

        log.info("开始秒杀下单，用户ID: {}, 商品ID: {}", userId, goodsId);

        // 使用分布式锁（锁的粒度：用户ID + 商品ID）
        String lockKey = RedisKeyConstant.getSeckillOrderLockKey(userId, goodsId);

        return redissonLockUtil.executeWithLock(lockKey, () -> {
            // 在锁内执行秒杀逻辑，确保同一用户对同一商品不会重复下单
            return doCreateOrder(userId, goodsId, orderDTO);
        });
    }

    /**
     * 实际的订单创建逻辑（在分布式锁保护下执行）
     */
    private SeckillOrderVO doCreateOrder(Long userId, Long goodsId, SeckillOrderDTO orderDTO) {
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
        // 虽然有分布式锁，但数据库也要检查（双重保险）
        SeckillOrder existOrder = seckillOrderMapper.selectOne(
                new QueryWrapper<SeckillOrder>()
                        .eq("user_id", userId)
                        .eq("goods_id", goodsId)
        );

        if (existOrder != null) {
            log.warn("用户已购买过该商品，用户ID: {}, 商品ID: {}", userId, goodsId);
            throw new RuntimeException("您已经购买过该商品了");
        }

        // 4. 使用Lua脚本原子扣减Redis库存（防止超卖的关键！）
        String stockKey = RedisKeyConstant.getSeckillStockKey(goodsId);
        Long luaResult = luaScriptUtil.deductStock(stockKey);

        if (LuaScriptUtil.isKeyNotExist(luaResult)) {
            log.error("Redis库存Key不存在，商品ID: {}, 请检查缓存预热是否正常", goodsId);
            throw new RuntimeException("系统异常，请稍后重试");
        }

        if (LuaScriptUtil.isStockInsufficient(luaResult)) {
            log.warn("Redis库存不足，商品ID: {}", goodsId);
            throw new RuntimeException("商品已售罄");
        }

        log.info("Lua脚本扣减Redis库存成功，商品ID: {}, 剩余库存: {}",
                goodsId, redisUtil.getLong(stockKey));

        // 5. 扣减MySQL库存（双重保险，保证最终一致性）
        int updateCount = seckillGoodsMapper.update(null,
                new UpdateWrapper<SeckillGoods>()
                        .setSql("stock_count = stock_count - 1")
                        .eq("goods_id", goodsId)
                        .gt("stock_count", 0)  // 库存必须大于0
        );

        if (updateCount == 0) {
            // MySQL库存不足，需要回滚Redis库存
            redisUtil.incr(stockKey, 1);
            log.warn("数据库库存不足，已回滚Redis库存，商品ID: {}", goodsId);
            throw new RuntimeException("商品已售罄");
        }

        log.info("MySQL库存扣减成功，商品ID: {}", goodsId);

        // 6. 查询商品基本信息（用于冗余字段）
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods == null) {
            log.error("商品不存在，商品ID: {}", goodsId);
            throw new RuntimeException("商品信息异常");
        }

        // 7. 创建订单
        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setSeckillGoodsId(seckillGoods.getId());
        order.setOrderNo(generateOrderNo());
        order.setOrderId(generateOrderId());
        order.setGoodsName(goods.getName());
        order.setSeckillPrice(seckillGoods.getSeckillPrice());
        order.setStatus(0);  // 0-待支付
        order.setSeckillStatus(0);  // 0-秒杀中
        order.setCreateTime(LocalDateTime.now());

        int insertCount = seckillOrderMapper.insert(order);

        if (insertCount == 0) {
            log.error("订单创建失败，用户ID: {}, 商品ID: {}", userId, goodsId);
            throw new RuntimeException("订单创建失败");
        }

        log.info("订单创建成功，订单号: {}, 订单ID: {}, 用户ID: {}, 商品ID: {}, 秒杀商品ID: {}",
                order.getOrderNo(), order.getOrderId(), userId, goodsId, seckillGoods.getId());

        // 8. 转换为VO返回
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
     * ✅ 新增：执行秒杀（集成令牌验证后的统一入口）
     *
     * 此方法在令牌验证通过后调用，执行完整的秒杀流程
     */
    @Override
    public String doSeckill(Long userId, Long goodsId) {
        log.info("=== 开始执行秒杀 === 用户ID: {}, 商品ID: {}", userId, goodsId);

        // 构造订单DTO
        SeckillOrderDTO orderDTO = new SeckillOrderDTO();
        orderDTO.setGoodsId(goodsId);

        try {
            // 调用现有的createOrder方法（已包含完整的秒杀逻辑）
            SeckillOrderVO orderVO = createOrder(userId, orderDTO);

            if (orderVO == null) {
                log.error("秒杀失败，订单创建返回null，用户ID: {}, 商品ID: {}", userId, goodsId);
                throw new RuntimeException("订单创建失败");
            }

            log.info("=== 秒杀成功 === 用户ID: {}, 商品ID: {}, 订单号: {}",
                    userId, goodsId, orderVO.getOrderNo());

            // 返回订单ID（供前端查询使用）
            return orderVO.getOrderId();

        } catch (RuntimeException e) {
            // 业务异常直接抛出（库存不足、重复下单等）
            log.warn("秒杀业务异常，用户ID: {}, 商品ID: {}, 原因: {}",
                    userId, goodsId, e.getMessage());
            throw e;

        } catch (Exception e) {
            // 系统异常统一处理
            log.error("秒杀系统异常，用户ID: {}, 商品ID: {}, 错误: ",
                    userId, goodsId, e);
            throw new RuntimeException("系统繁忙，请稍后重试");
        }
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
     * 生成订单ID（用于支付系统）
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