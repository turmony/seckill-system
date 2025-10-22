package io.github.turmony.seckillsystem.controller;


import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.dto.SeckillOrderDTO;
import io.github.turmony.seckillsystem.service.SeckillOrderService;
import io.github.turmony.seckillsystem.vo.SeckillOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 秒杀订单控制器
 */
@Slf4j
@RestController
@RequestMapping("/seckill/order")
@RequiredArgsConstructor
public class SeckillOrderController {

    /**
     * 秒杀令牌（一次性）
     */
    private String token;

    private final SeckillOrderService seckillOrderService;

    /**
     * 秒杀下单接口（基础版）
     * @param orderDTO 下单请求参数
     * @param request HTTP请求（用于获取用户ID）
     * @return 订单信息
     */
    @PostMapping("/create")
    public Result<SeckillOrderVO> createOrder(
            @Validated @RequestBody SeckillOrderDTO orderDTO,
            HttpServletRequest request) {

        // 从请求中获取用户ID（由JWT拦截器设置）
        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            log.warn("用户未登录，无法下单");
            return Result.error("请先登录");
        }

        log.info("用户下单请求，用户ID: {}, 商品ID: {}", userId, orderDTO.getGoodsId());

        try {
            SeckillOrderVO orderVO = seckillOrderService.createOrder(userId, orderDTO);
            return Result.success(orderVO);

        } catch (RuntimeException e) {
            log.error("秒杀下单失败，用户ID: {}, 商品ID: {}, 错误: {}",
                    userId, orderDTO.getGoodsId(), e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("秒杀下单异常，用户ID: {}, 商品ID: {}", userId, orderDTO.getGoodsId(), e);
            return Result.error("系统繁忙，请稍后再试");
        }
    }

    /**
     * 查询用户在指定商品的订单
     * @param goodsId 商品ID
     * @param request HTTP请求
     * @return 订单信息
     */
    @GetMapping("/query/{goodsId}")
    public Result<SeckillOrderVO> getOrder(
            @PathVariable Long goodsId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            return Result.error("请先登录");
        }

        log.info("查询订单，用户ID: {}, 商品ID: {}", userId, goodsId);

        SeckillOrderVO orderVO = seckillOrderService.getOrderByUserIdAndGoodsId(userId, goodsId);

        if (orderVO == null) {
            return Result.error("订单不存在");
        }

        return Result.success(orderVO);
    }
}
