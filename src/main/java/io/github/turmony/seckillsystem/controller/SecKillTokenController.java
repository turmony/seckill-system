package io.github.turmony.seckillsystem.controller;

import io.github.turmony.seckillsystem.common.RateLimit;
import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.dto.SecKillTokenDTO;
import io.github.turmony.seckillsystem.service.SecKillTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀令牌控制器
 */
@Slf4j
@RestController
@RequestMapping("/seckill/token")
public class SecKillTokenController {

    @Autowired
    private SecKillTokenService secKillTokenService;

    /**
     * 生成秒杀令牌
     * 用户访问秒杀商品详情页时调用此接口获取令牌
     *
     * @param userId 用户ID（从JWT Token中获取）
     * @param goodsId 商品ID
     * @return 令牌信息
     */
    @GetMapping("/generate")
    @RateLimit(permitsPerSecond = 500, message = "请求过于频繁")
    public Result<SecKillTokenDTO> generateToken(
            @RequestParam Long userId,
            @RequestParam Long goodsId) {

        log.info("生成秒杀令牌请求, userId:{}, goodsId:{}", userId, goodsId);

        // 生成令牌
        String token = secKillTokenService.generateToken(userId, goodsId);

        // 封装返回结果
        SecKillTokenDTO tokenDTO = new SecKillTokenDTO();
        tokenDTO.setToken(token);
        tokenDTO.setGoodsId(goodsId);
        tokenDTO.setExpireSeconds(300L); // 5分钟
        tokenDTO.setTimestamp(System.currentTimeMillis());

        return Result.success(tokenDTO);
    }
}