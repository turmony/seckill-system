package io.github.turmony.seckillsystem.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import io.github.turmony.seckillsystem.common.RedisKeyConstant;
import io.github.turmony.seckillsystem.dto.SeckillMessageDTO;
import io.github.turmony.seckillsystem.dto.SeckillOrderDTO;
import io.github.turmony.seckillsystem.entity.Goods;
import io.github.turmony.seckillsystem.entity.SeckillGoods;
import io.github.turmony.seckillsystem.entity.SeckillOrder;
import io.github.turmony.seckillsystem.mapper.GoodsMapper;
import io.github.turmony.seckillsystem.mapper.SeckillGoodsMapper;
import io.github.turmony.seckillsystem.mapper.SeckillOrderMapper;
import io.github.turmony.seckillsystem.service.SeckillOrderService;
import io.github.turmony.seckillsystem.service.mq.MQSender;
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
 * Step 14改造：集成RabbitMQ异步处理，实现削峰填谷
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
    private final MQSender mqSender;  // 新增：消息发送者

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
     * 注意：此方法在Step 14后主要用于测试，正常秒杀走异步流程
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
     * ✅ Step 14 核心改造：执行秒杀（异步模式）
     *
     * 异步秒杀流程：
     * 1. 校验秒杀时间
     * 2. 检查是否重复下单
     * 3. Lua脚本扣减Redis库存
     * 4. 创建"排队中"状态的订单
     * 5. 发送MQ消息
     * 6. 立即返回订单ID（不等待订单处理完成）
     *
     * MQ消费者会异步处理：
     * - 扣减MySQL库存
     * - 更新订单状态为成功/失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String doSeckill(Long userId, Long goodsId) {
        log.info("=== 【异步秒杀】开始 === 用户ID: {}, 商品ID: {}", userId, goodsId);

        // ============ Step 1: 查询秒杀商品信息 ============
        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                new QueryWrapper<SeckillGoods>().eq("goods_id", goodsId)
        );

        if (seckillGoods == null) {
            log.warn("❌ 秒杀商品不存在，商品ID: {}", goodsId);
            throw new RuntimeException("秒杀商品不存在");
        }

        // ============ Step 2: 校验秒杀时间 ============
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(seckillGoods.getStartTime())) {
            log.warn("❌ 秒杀未开始，商品ID: {}, 开始时间: {}", goodsId, seckillGoods.getStartTime());
            throw new RuntimeException("秒杀活动尚未开始");
        }
        if (now.isAfter(seckillGoods.getEndTime())) {
            log.warn("❌ 秒杀已结束，商品ID: {}, 结束时间: {}", goodsId, seckillGoods.getEndTime());
            throw new RuntimeException("秒杀活动已结束");
        }

        log.info("✅ 秒杀时间校验通过");

        // ============ Step 3: 检查是否重复下单 ============
        SeckillOrder existOrder = seckillOrderMapper.selectOne(
                new QueryWrapper<SeckillOrder>()
                        .eq("user_id", userId)
                        .eq("goods_id", goodsId)
        );

        if (existOrder != null) {
            log.warn("❌ 用户已有订单，用户ID: {}, 商品ID: {}, 订单状态: {}",
                    userId, goodsId, existOrder.getStatus());

            // 如果已有排队中或成功的订单，返回该订单ID
            if (existOrder.getStatus() == 0 || existOrder.getStatus() == 1) {
                log.info("返回已存在的订单ID: {}", existOrder.getOrderId());
                return existOrder.getOrderId();
            }
            // 如果之前失败了，允许重新秒杀
        }

        log.info("✅ 重复下单检查通过");

        // ============ Step 4: Lua脚本扣减Redis库存 ============
        String stockKey = RedisKeyConstant.getSeckillStockKey(goodsId);
        Long luaResult = luaScriptUtil.deductStock(stockKey);

        if (LuaScriptUtil.isKeyNotExist(luaResult)) {
            log.error("❌ Redis库存Key不存在，商品ID: {}", goodsId);
            throw new RuntimeException("系统异常，请稍后重试");
        }

        if (LuaScriptUtil.isStockInsufficient(luaResult)) {
            log.warn("❌ Redis库存不足，商品ID: {}", goodsId);
            throw new RuntimeException("商品已售罄");
        }

        log.info("✅ Lua脚本扣减Redis库存成功，商品ID: {}, 剩余库存: {}",
                goodsId, redisUtil.getLong(stockKey));

        // ============ Step 5: 创建"排队中"状态的订单 ============
        String orderId = generateOrderId();
        String orderNo = generateOrderNo();

        SeckillOrder order = new SeckillOrder();
        order.setUserId(userId);
        order.setGoodsId(goodsId);
        order.setSeckillGoodsId(seckillGoods.getId());
        order.setOrderNo(orderNo);
        order.setOrderId(orderId);

        // 查询商品基本信息
        Goods goods = goodsMapper.selectById(goodsId);
        if (goods != null) {
            order.setGoodsName(goods.getName());
        }

        order.setSeckillPrice(seckillGoods.getSeckillPrice());
        order.setStatus(0);  // 0-排队中（等待MQ消费者处理）
        order.setSeckillStatus(0);  // 0-秒杀中
        order.setCreateTime(LocalDateTime.now());

        int insertCount = seckillOrderMapper.insert(order);

        if (insertCount == 0) {
            // 订单创建失败，需要回滚Redis库存
            redisUtil.incr(stockKey, 1);
            log.error("❌ 订单创建失败，已回滚Redis库存，用户ID: {}, 商品ID: {}", userId, goodsId);
            throw new RuntimeException("订单创建失败");
        }

        log.info("✅ 订单创建成功（排队中状态），订单ID: {}, 订单号: {}", orderId, orderNo);

        // ============ Step 6: 发送MQ消息（异步处理） ============
        try {
            SeckillMessageDTO message = new SeckillMessageDTO();
            message.setUserId(userId);
            message.setGoodsId(goodsId);
            message.setOrderId(orderId);
            message.setTimestamp(System.currentTimeMillis());

            mqSender.sendSeckillMessage(message);

            log.info("✅ 秒杀消息已发送到MQ，订单ID: {}", orderId);

        } catch (Exception e) {
            log.error("❌ MQ消息发送失败，订单ID: {}, 错误: {}", orderId, e.getMessage(), e);
            // 消息发送失败，更新订单状态为失败
            seckillOrderMapper.update(null,
                    new UpdateWrapper<SeckillOrder>()
                            .set("status", 2)  // 2-失败
                            .eq("order_id", orderId)
            );
            throw new RuntimeException("系统繁忙，请稍后重试");
        }

        log.info("=== 【异步秒杀】完成 === 订单ID: {}，请稍后查询订单结果", orderId);

        // ============ Step 7: 立即返回订单ID（不阻塞用户） ============
        return orderId;
    }

    /**
     * ✅ Step 14 新增：处理秒杀订单（MQ消费者调用）
     *
     * 此方法由MQ消费者异步调用，完成订单的最终处理
     *
     * 处理流程：
     * 1. 使用Redisson分布式锁（防止重复消费）
     * 2. 扣减MySQL库存
     * 3. 更新订单状态为成功或失败
     * 4. 记录操作日志
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processOrder(Long userId, Long goodsId, String orderId) {
        log.info("=== 【MQ消费者】开始处理订单 === 订单ID: {}, 用户ID: {}, 商品ID: {}",
                orderId, userId, goodsId);

        // ============ Step 1: 使用分布式锁（防止重复消费） ============
        String lockKey = RedisKeyConstant.getSeckillOrderLockKey(userId, goodsId);

        redissonLockUtil.executeWithLock(lockKey, () -> {
            try {
                // ============ Step 2: 查询订单 ============
                SeckillOrder order = seckillOrderMapper.selectOne(
                        new QueryWrapper<SeckillOrder>().eq("order_id", orderId)
                );

                if (order == null) {
                    log.error("❌ 订单不存在，订单ID: {}", orderId);
                    return null;
                }

                // 如果订单已经处理过（状态不是排队中），直接返回
                if (order.getStatus() != 0) {
                    log.warn("⚠️ 订单已处理过，订单ID: {}, 当前状态: {}", orderId, order.getStatus());
                    return null;
                }

                // ============ Step 3: 扣减MySQL库存 ============
                int updateCount = seckillGoodsMapper.update(null,
                        new UpdateWrapper<SeckillGoods>()
                                .setSql("stock_count = stock_count - 1")
                                .eq("goods_id", goodsId)
                                .gt("stock_count", 0)
                );

                if (updateCount == 0) {
                    // MySQL库存不足，更新订单状态为失败，并回滚Redis库存
                    log.warn("❌ MySQL库存不足，订单处理失败，订单ID: {}", orderId);

                    // 回滚Redis库存
                    String stockKey = RedisKeyConstant.getSeckillStockKey(goodsId);
                    redisUtil.incr(stockKey, 1);

                    // 更新订单状态为失败
                    seckillOrderMapper.update(null,
                            new UpdateWrapper<SeckillOrder>()
                                    .set("status", 2)  // 2-失败
                                    .set("seckill_status", 2)  // 2-秒杀失败
                                    .set("update_time", LocalDateTime.now())
                                    .eq("order_id", orderId)
                    );

                    log.info("订单状态已更新为失败，订单ID: {}", orderId);
                    return null;
                }

                log.info("✅ MySQL库存扣减成功，商品ID: {}", goodsId);

                // ============ Step 4: 更新订单状态为成功 ============
                seckillOrderMapper.update(null,
                        new UpdateWrapper<SeckillOrder>()
                                .set("status", 1)  // 1-成功
                                .set("seckill_status", 1)  // 1-秒杀成功
                                .set("update_time", LocalDateTime.now())
                                .eq("order_id", orderId)
                );

                log.info("✅ 订单处理成功，订单ID: {}, 订单号: {}", orderId, order.getOrderNo());

                return null;

            } catch (Exception e) {
                log.error("❌ 订单处理异常，订单ID: {}, 错误: {}", orderId, e.getMessage(), e);

                // 更新订单状态为失败
                seckillOrderMapper.update(null,
                        new UpdateWrapper<SeckillOrder>()
                                .set("status", 2)  // 2-失败
                                .set("seckill_status", 2)  // 2-秒杀失败
                                .set("update_time", LocalDateTime.now())
                                .eq("order_id", orderId)
                );

                // 回滚Redis库存
                String stockKey = RedisKeyConstant.getSeckillStockKey(goodsId);
                redisUtil.incr(stockKey, 1);

                throw new RuntimeException("订单处理失败: " + e.getMessage(), e);
            }
        });

        log.info("=== 【MQ消费者】订单处理完成 === 订单ID: {}", orderId);
    }

    /**
     * ✅ Step 14 新增：根据订单ID查询订单
     */
    @Override
    public SeckillOrderVO getOrderByOrderId(String orderId) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new QueryWrapper<SeckillOrder>().eq("order_id", orderId)
        );

        if (order == null) {
            return null;
        }

        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                new QueryWrapper<SeckillGoods>().eq("goods_id", order.getGoodsId())
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