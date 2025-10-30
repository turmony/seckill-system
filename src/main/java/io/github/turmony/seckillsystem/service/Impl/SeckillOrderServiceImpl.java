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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 秒杀订单服务实现类
 * Step 15改造：新增订单查询功能
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
    private final MQSender mqSender;

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

        // ============ Step 3: 检查是否已经购买过（防止重复下单） ============
        SeckillOrder existOrder = seckillOrderMapper.selectOne(
                new QueryWrapper<SeckillOrder>()
                        .eq("user_id", userId)
                        .eq("goods_id", goodsId)
        );

        if (existOrder != null) {
            log.warn("⚠️ 用户已购买过该商品，返回现有订单，用户ID: {}, 商品ID: {}", userId, goodsId);
            return existOrder.getOrderId();
        }

        // ============ Step 4: 使用Lua脚本原子扣减Redis库存 ============
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

        log.info("✅ Lua脚本扣减Redis库存成功，商品ID: {}", goodsId);

        // ============ Step 5: 创建"排队中"状态的订单 ============
        String orderNo = generateOrderNo();
        String orderId = generateOrderId();

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
        order.setStatus(0);  // 0-排队中
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

    // ==================== Step 15 新增方法 ====================

    /**
     * ✅ Step 15: 查询用户的订单列表
     * 功能：查询指定用户的所有秒杀订单
     *
     * @param userId 用户ID
     * @return 订单列表（按创建时间倒序）
     */
    @Override
    public List<SeckillOrderVO> getUserOrders(Long userId) {
        log.info("查询用户订单列表，用户ID: {}", userId);

        // 查询用户的所有订单，按创建时间倒序排列
        List<SeckillOrder> orderList = seckillOrderMapper.selectList(
                new QueryWrapper<SeckillOrder>()
                        .eq("user_id", userId)
                        .orderByDesc("create_time")
        );

        if (orderList == null || orderList.isEmpty()) {
            log.info("用户暂无订单，用户ID: {}", userId);
            return new ArrayList<>();
        }

        log.info("查询到用户订单数量: {}, 用户ID: {}", orderList.size(), userId);

        // 转换为VO并返回
        return orderList.stream()
                .map(this::convertToVOWithGoods)
                .collect(Collectors.toList());
    }

    /**
     * ✅ Step 15: 根据状态查询用户的订单列表
     * 功能：支持按订单状态筛选订单
     *
     * @param userId 用户ID
     * @param status 订单状态：0-排队中 1-成功 2-失败 null-全部
     * @return 订单列表（按创建时间倒序）
     */
    @Override
    public List<SeckillOrderVO> getUserOrdersByStatus(Long userId, Integer status) {
        log.info("根据状态查询用户订单列表，用户ID: {}, 状态: {}", userId, status);

        QueryWrapper<SeckillOrder> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);

        // 如果指定了状态，添加状态筛选条件
        if (status != null) {
            queryWrapper.eq("status", status);
        }

        // 按创建时间倒序排列
        queryWrapper.orderByDesc("create_time");

        List<SeckillOrder> orderList = seckillOrderMapper.selectList(queryWrapper);

        if (orderList == null || orderList.isEmpty()) {
            log.info("未查询到符合条件的订单，用户ID: {}, 状态: {}", userId, status);
            return new ArrayList<>();
        }

        log.info("查询到订单数量: {}, 用户ID: {}, 状态: {}", orderList.size(), userId, status);

        // 转换为VO并返回
        return orderList.stream()
                .map(this::convertToVOWithGoods)
                .collect(Collectors.toList());
    }

    /**
     * ✅ Step 15: 查询订单详情（根据订单ID）
     * 功能：查询指定订单的详细信息，同时验证订单归属
     *
     * @param userId 用户ID（用于权限验证）
     * @param orderId 订单ID
     * @return 订单详情
     */
    @Override
    public SeckillOrderVO getOrderDetail(Long userId, String orderId) {
        log.info("查询订单详情，用户ID: {}, 订单ID: {}", userId, orderId);

        // 查询订单（同时验证用户ID和订单ID）
        SeckillOrder order = seckillOrderMapper.selectOne(
                new QueryWrapper<SeckillOrder>()
                        .eq("user_id", userId)
                        .eq("order_id", orderId)
        );

        if (order == null) {
            log.warn("订单不存在或无权访问，用户ID: {}, 订单ID: {}", userId, orderId);
            return null;
        }

        log.info("查询到订单详情，订单号: {}, 状态: {}", order.getOrderNo(), order.getStatus());

        // 转换为VO并返回
        return convertToVOWithGoods(order);
    }

    // ==================== 私有辅助方法 ====================

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
     * 转换为VO（原有方法，保留用于向后兼容）
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

    /**
     * ✅ Step 15 新增：转换为VO（增强版，自动查询关联商品信息）
     * 功能：根据订单实体转换为VO，同时查询关联的商品和秒杀商品信息
     *
     * @param order 订单实体
     * @return 订单VO
     */
    private SeckillOrderVO convertToVOWithGoods(SeckillOrder order) {
        SeckillOrderVO vo = new SeckillOrderVO();
        BeanUtils.copyProperties(order, vo);

        // 查询秒杀商品信息
        SeckillGoods seckillGoods = seckillGoodsMapper.selectOne(
                new QueryWrapper<SeckillGoods>().eq("goods_id", order.getGoodsId())
        );

        if (seckillGoods != null) {
            // 设置秒杀价格
            vo.setSeckillPrice(seckillGoods.getSeckillPrice());
        }

        // 查询商品基本信息
        Goods goods = goodsMapper.selectById(order.getGoodsId());
        if (goods != null) {
            vo.setGoodsName(goods.getName());
            vo.setGoodsImg(goods.getImg());
        }

        return vo;
    }
}