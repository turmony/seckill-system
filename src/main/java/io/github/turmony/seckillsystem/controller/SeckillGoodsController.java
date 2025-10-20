package io.github.turmony.seckillsystem.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.service.SeckillGoodsService;
import io.github.turmony.seckillsystem.vo.SeckillGoodsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀商品控制器
 * 提供秒杀商品列表和详情查询接口
 */
@Slf4j
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillGoodsController {

    private final SeckillGoodsService seckillGoodsService;

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
}
