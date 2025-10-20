package io.github.turmony.seckillsystem.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.turmony.seckillsystem.common.Result;
import io.github.turmony.seckillsystem.entity.Goods;
import io.github.turmony.seckillsystem.service.GoodsService;
import io.github.turmony.seckillsystem.vo.GoodsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商品控制器
 */
@RestController
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private final GoodsService goodsService;

    /**
     * 分页查询商品列表
     * @param current 当前页，默认第1页
     * @param size 每页大小，默认10条
     */
    @GetMapping("/list")
    public Result<Page<GoodsVO>> getGoodsList(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size) {

        Page<GoodsVO> page = goodsService.getGoodsList(current, size);
        return Result.success(page);
    }

    /**
     * 查询商品详情
     * @param id 商品ID
     */
    @GetMapping("/{id}")
    public Result<GoodsVO> getGoodsById(@PathVariable Long id) {
        GoodsVO goods = goodsService.getGoodsById(id);
        if (goods == null) {
            return Result.error("商品不存在");
        }
        return Result.success(goods);
    }

    /**
     * 添加商品（需要登录）
     */
    @PostMapping("/add")
    public Result<String> addGoods(@RequestBody Goods goods) {
        // 参数校验
        if (goods.getName() == null || goods.getPrice() == null) {
            return Result.error("商品名称和价格不能为空");
        }

        boolean success = goodsService.addGoods(goods);
        return success ? Result.success("添加成功") : Result.error("添加失败");
    }

    /**
     * 更新商品（需要登录）
     */
    @PutMapping("/update")
    public Result<String> updateGoods(@RequestBody Goods goods) {
        if (goods.getId() == null) {
            return Result.error("商品ID不能为空");
        }

        boolean success = goodsService.updateGoods(goods);
        return success ? Result.success("更新成功") : Result.error("更新失败");
    }

    /**
     * 删除商品（需要登录）
     */
    @DeleteMapping("/{id}")
    public Result<String> deleteGoods(@PathVariable Long id) {
        boolean success = goodsService.deleteGoods(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }
}
