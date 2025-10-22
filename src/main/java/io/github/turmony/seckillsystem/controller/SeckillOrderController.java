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
import java.util.List;

/**
 * 秒杀订单控制器 - 修复后版本（无编译错误）
 * ✅ 已删除 setSeckillStatus() 调用
 */
@Slf4j
@RestController
@RequestMapping("/seckill/order")
@RequiredArgsConstructor
public class SeckillOrderController {

    private final SeckillOrderService seckillOrderService;

    /**
     * ✅ 异步秒杀下单接口（修复后 - 无编译错误版本）
     *
     * 核心改动：
     * 1. 调用 doSeckill() 而不是 createOrder()
     * 2. 立即返回"排队中"状态
     * 3. 删除了 setSeckillStatus() 调用（该方法不存在）
     */
    @PostMapping("/create")
    public Result<SeckillOrderVO> createOrder(
            @Validated @RequestBody SeckillOrderDTO orderDTO,
            HttpServletRequest request) {

        // 获取用户ID
        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            log.warn("用户未登录,无法下单");
            return Result.error("请先登录");
        }

        log.info("【异步秒杀】用户下单请求,用户ID: {}, 商品ID: {}", userId, orderDTO.getGoodsId());

        try {
            // ✅ 调用异步秒杀方法（立即返回订单ID）
            String orderId = seckillOrderService.doSeckill(userId, orderDTO.getGoodsId());

            // ✅ 构造"排队中"状态的VO
            SeckillOrderVO orderVO = new SeckillOrderVO();
            orderVO.setOrderId(orderId);
            orderVO.setUserId(userId);
            orderVO.setGoodsId(orderDTO.getGoodsId());
            orderVO.setStatus(0);  // 0-排队中（关键状态！）

            log.info("【异步秒杀】订单已创建(排队中),订单ID: {}", orderId);

            return Result.success("秒杀请求已提交,请稍后查询订单状态", orderVO);

        } catch (RuntimeException e) {
            log.error("秒杀下单失败,用户ID: {}, 商品ID: {}, 错误: {}",
                    userId, orderDTO.getGoodsId(), e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("秒杀下单异常,用户ID: {}, 商品ID: {}", userId, orderDTO.getGoodsId(), e);
            return Result.error("系统繁忙,请稍后再试");
        }
    }

    /**
     * 查询用户在指定商品的订单
     */
    @GetMapping("/query/{goodsId}")
    public Result<SeckillOrderVO> getOrder(
            @PathVariable Long goodsId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            return Result.error("请先登录");
        }

        log.info("查询订单,用户ID: {}, 商品ID: {}", userId, goodsId);

        SeckillOrderVO orderVO = seckillOrderService.getOrderByUserIdAndGoodsId(userId, goodsId);

        if (orderVO == null) {
            return Result.error("订单不存在");
        }

        return Result.success(orderVO);
    }

    /**
     * ✅ 我的订单列表
     */
    @GetMapping("/list")
    public Result<List<SeckillOrderVO>> getUserOrders(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            log.warn("用户未登录,无法查询订单列表");
            return Result.error("请先登录");
        }

        log.info("查询用户订单列表,用户ID: {}", userId);

        try {
            List<SeckillOrderVO> orderList = seckillOrderService.getUserOrders(userId);
            log.info("成功返回订单列表,用户ID: {}, 订单数量: {}", userId, orderList.size());
            return Result.success("查询成功", orderList);

        } catch (Exception e) {
            log.error("查询订单列表失败,用户ID: {}, 错误: {}", userId, e.getMessage(), e);
            return Result.error("查询订单列表失败");
        }
    }

    /**
     * ✅ 根据状态查询订单列表
     */
    @GetMapping("/list/status")
    public Result<List<SeckillOrderVO>> getUserOrdersByStatus(
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            log.warn("用户未登录,无法查询订单列表");
            return Result.error("请先登录");
        }

        if (status != null && (status < 0 || status > 2)) {
            log.warn("订单状态参数无效,用户ID: {}, 状态: {}", userId, status);
            return Result.error("订单状态参数无效,有效值: 0-排队中, 1-成功, 2-失败");
        }

        log.info("根据状态查询订单列表,用户ID: {}, 状态: {}", userId, status);

        try {
            List<SeckillOrderVO> orderList = seckillOrderService.getUserOrdersByStatus(userId, status);

            String statusDesc = getStatusDescription(status);
            log.info("成功返回订单列表,用户ID: {}, 状态: {}, 订单数量: {}",
                    userId, statusDesc, orderList.size());

            return Result.success("查询成功", orderList);

        } catch (Exception e) {
            log.error("查询订单列表失败,用户ID: {}, 状态: {}, 错误: {}",
                    userId, status, e.getMessage(), e);
            return Result.error("查询订单列表失败");
        }
    }

    /**
     * ✅ 订单详情
     */
    @GetMapping("/detail/{orderId}")
    public Result<SeckillOrderVO> getOrderDetail(
            @PathVariable String orderId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            log.warn("用户未登录,无法查询订单详情");
            return Result.error("请先登录");
        }

        if (orderId == null || orderId.trim().isEmpty()) {
            log.warn("订单ID为空,用户ID: {}", userId);
            return Result.error("订单ID不能为空");
        }

        log.info("查询订单详情,用户ID: {}, 订单ID: {}", userId, orderId);

        try {
            SeckillOrderVO orderVO = seckillOrderService.getOrderDetail(userId, orderId);

            if (orderVO == null) {
                log.warn("订单不存在或无权访问,用户ID: {}, 订单ID: {}", userId, orderId);
                return Result.error("订单不存在或无权访问");
            }

            log.info("成功返回订单详情,用户ID: {}, 订单号: {}, 状态: {}",
                    userId, orderVO.getOrderNo(), orderVO.getStatus());

            return Result.success("查询成功", orderVO);

        } catch (Exception e) {
            log.error("查询订单详情失败,用户ID: {}, 订单ID: {}, 错误: {}",
                    userId, orderId, e.getMessage(), e);
            return Result.error("查询订单详情失败");
        }
    }

    /**
     * ✅ 查询订单结果（异步秒杀核心接口）
     *
     * 前端轮询此接口查询订单最终状态
     */
    @GetMapping("/result/{orderId}")
    public Result<SeckillOrderVO> getOrderResult(
            @PathVariable String orderId,
            HttpServletRequest request) {

        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            log.warn("用户未登录,无法查询订单结果");
            return Result.error("请先登录");
        }

        log.info("【异步轮询】查询订单结果,用户ID: {}, 订单ID: {}", userId, orderId);

        try {
            SeckillOrderVO orderVO = seckillOrderService.getOrderByOrderId(orderId);

            if (orderVO == null) {
                log.warn("订单不存在,用户ID: {}, 订单ID: {}", userId, orderId);
                return Result.error("订单不存在");
            }

            // 验证订单归属
            if (!orderVO.getUserId().equals(userId)) {
                log.warn("无权访问该订单,用户ID: {}, 订单用户ID: {}, 订单ID: {}",
                        userId, orderVO.getUserId(), orderId);
                return Result.error("无权访问该订单");
            }

            // 根据订单状态返回不同的提示信息
            String message;
            switch (orderVO.getStatus()) {
                case 0:
                    message = "订单排队中,请稍候...";
                    log.info("【异步轮询】订单仍在排队中,订单ID: {}", orderId);
                    break;
                case 1:
                    message = "秒杀成功!";
                    log.info("【异步轮询】订单处理成功,订单ID: {}", orderId);
                    break;
                case 2:
                    message = "秒杀失败,请重试";
                    log.info("【异步轮询】订单处理失败,订单ID: {}", orderId);
                    break;
                default:
                    message = "查询成功";
            }

            return Result.success(message, orderVO);

        } catch (Exception e) {
            log.error("查询订单结果失败,用户ID: {}, 订单ID: {}, 错误: {}",
                    userId, orderId, e.getMessage(), e);
            return Result.error("查询订单结果失败");
        }
    }

    /**
     * 获取订单状态描述
     */
    private String getStatusDescription(Integer status) {
        if (status == null) {
            return "全部";
        }
        switch (status) {
            case 0:
                return "排队中";
            case 1:
                return "成功";
            case 2:
                return "失败";
            default:
                return "未知";
        }
    }
}