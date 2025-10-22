package io.github.turmony.seckillsystem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.turmony.seckillsystem.common.RateLimit;
import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.dto.SeckillOrderDTO;
import io.github.turmony.seckillsystem.service.SeckillGoodsService;
import io.github.turmony.seckillsystem.service.SeckillOrderService;
import io.github.turmony.seckillsystem.service.SecKillTokenService;
import io.github.turmony.seckillsystem.vo.SeckillGoodsVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀商品控制器
 * 提供秒杀商品列表、详情查询和秒杀下单接口
 */
@Slf4j
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillGoodsController {

    private final SeckillGoodsService seckillGoodsService;
    private final SeckillOrderService seckillOrderService;
    private final SecKillTokenService secKillTokenService;

    /**
     * 获取秒杀商品列表（从Redis读取）
     * @param current 当前页，默认第1页
     * @param size 每页大小，默认10条
     * @return 分页的秒杀商品列表
     */
    @GetMapping("/list")
    public Result<Page<SeckillGoodsVO>> getSeckillGoodsList(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {

        log.info("查询秒杀商品列表，当前页: {}, 每页大小: {}", current, size);
        Page<SeckillGoodsVO> page = seckillGoodsService.getSeckillGoodsList(current, size);
        return Result.success(page);
    }

    /**
     * 获取秒杀商品详情（从Redis读取）
     * @param goodsId 商品ID
     * @return 秒杀商品详情VO，包含实时库存和倒计时信息
     */
    @GetMapping("/detail/{goodsId}")
    public Result<SeckillGoodsVO> getSeckillGoodsDetail(@PathVariable Long goodsId) {
        log.info("查询秒杀商品详情，商品ID: {}", goodsId);

        SeckillGoodsVO seckillGoodsVO = seckillGoodsService.getSeckillGoodsByGoodsId(goodsId);

        if (seckillGoodsVO == null) {
            log.warn("秒杀商品不存在，商品ID: {}", goodsId);
            return Result.error("秒杀商品不存在");
        }

        return Result.success(seckillGoodsVO);
    }

    /**
     * 秒杀下单接口
     *
     * 完整流程：
     * 1. 【接口幂等性】验证秒杀令牌（一次性Token）
     * 2. 【时间校验】验证秒杀是否在有效时间内
     * 3. 【库存扣减】Lua脚本原子性扣减Redis库存
     * 4. 【异步处理】发送MQ消息，异步创建订单
     * 5. 【快速响应】返回"排队中"状态，不阻塞用户
     *
     * @param userId 用户ID（实际项目中应从JWT Token中获取，这里为了测试方便使用参数传递）
     * @param orderDTO 秒杀请求参数（包含goodsId和token）
     * @return 秒杀结果（订单ID或错误信息）
     */
    @PostMapping("/order")
    @RateLimit(permitsPerSecond = 1000, message = "秒杀人数过多，请稍后重试")
    public Result<String> seckill(
            @RequestParam Long userId,
            @Valid @RequestBody SeckillOrderDTO orderDTO) {

        log.info("=== 秒杀请求开始 === 用户ID: {}, 商品ID: {}", userId, orderDTO.getGoodsId());

        // ============ Step 1: 验证秒杀令牌（接口幂等性保证） ============
        boolean tokenValid = secKillTokenService.validateAndConsumeToken(
                userId, orderDTO.getGoodsId(), orderDTO.getToken());

        if (!tokenValid) {
            log.warn("❌ 秒杀令牌验证失败，用户ID: {}, 商品ID: {}", userId, orderDTO.getGoodsId());
            return Result.error("秒杀令牌无效或已使用，请刷新页面重新获取秒杀资格");
        }

        log.info("✅ 秒杀令牌验证通过，用户ID: {}, 商品ID: {}", userId, orderDTO.getGoodsId());

        // ============ Step 2-5: 执行秒杀逻辑 ============
        // 包含：时间校验 → Lua扣减库存 → 分布式锁 → 发送MQ消息
        try {
            String orderId = seckillOrderService.doSeckill(userId, orderDTO.getGoodsId());

            log.info("✅ 秒杀请求提交成功，用户ID: {}, 商品ID: {}, 订单ID: {}",
                    userId, orderDTO.getGoodsId(), orderId);

            return Result.success(orderId, "秒杀请求已提交，请稍后在【我的订单】中查询结果");

        } catch (Exception e) {
            log.error("❌ 秒杀失败，用户ID: {}, 商品ID: {}, 原因: {}",
                    userId, orderDTO.getGoodsId(), e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}