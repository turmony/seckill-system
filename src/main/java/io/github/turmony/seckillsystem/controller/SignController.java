package io.github.turmony.seckillsystem.controller;

import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.util.SignUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 签名工具接口
 * 提供签名生成功能，供前端测试使用
 *
 * @author turmony
 */
@Slf4j
@RestController
@RequestMapping("/api/sign")
public class SignController {

    /**
     * 生成签名（用于前端测试）
     * 注意：生产环境中，签名应该由前端根据约定的规则自己生成，此接口仅供开发测试使用
     */
    @PostMapping("/generate")
    public Result<Map<String, Object>> generateSign(@RequestBody Map<String, String> params) {
        try {
            // 获取当前时间戳
            long timestamp = SignUtil.getCurrentTimestamp();

            // 生成签名
            String sign = SignUtil.generateSign(params, timestamp);

            Map<String, Object> result = new HashMap<>();
            result.put("sign", sign);
            result.put("timestamp", timestamp);
            result.put("params", params);

            log.info("生成签名成功: sign={}, timestamp={}, params={}", sign, timestamp, params);

            return Result.success(result);
        } catch (Exception e) {
            log.error("生成签名失败", e);
            return Result.error("生成签名失败: " + e.getMessage());
        }
    }

    /**
     * 生成简单签名（用于秒杀接口）
     */
    @GetMapping("/generateSimple")
    public Result<Map<String, Object>> generateSimpleSign(
            @RequestParam Long userId,
            @RequestParam Long goodsId) {
        try {
            long timestamp = SignUtil.getCurrentTimestamp();
            String sign = SignUtil.generateSimpleSign(userId, goodsId, timestamp);

            Map<String, Object> result = new HashMap<>();
            result.put("sign", sign);
            result.put("timestamp", timestamp);
            result.put("userId", userId);
            result.put("goodsId", goodsId);

            log.info("生成简单签名成功: sign={}, userId={}, goodsId={}, timestamp={}",
                    sign, userId, goodsId, timestamp);

            return Result.success(result);
        } catch (Exception e) {
            log.error("生成简单签名失败", e);
            return Result.error("生成简单签名失败: " + e.getMessage());
        }
    }

    /**
     * 验证签名（用于测试）
     */
    @PostMapping("/verify")
    public Result<Boolean> verifySign(@RequestBody Map<String, String> params) {
        try {
            boolean isValid = SignUtil.verifySign(params);
            return Result.success(isValid);
        } catch (Exception e) {
            log.error("验证签名失败", e);
            return Result.error("验证签名失败: " + e.getMessage());
        }
    }
}