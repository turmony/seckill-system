package io.github.turmony.seckillsystem.util;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;

/**
 * Lua脚本工具类
 * 用于执行Redis Lua脚本，保证操作的原子性
 *
 * @author turmony
 */
@Slf4j
@Component
public class LuaScriptUtil {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 库存扣减Lua脚本
     */
    private DefaultRedisScript<Long> stockDeductScript;

    /**
     * 初始化Lua脚本
     */
    @PostConstruct
    public void init() {
        stockDeductScript = new DefaultRedisScript<>();
        stockDeductScript.setResultType(Long.class);
        stockDeductScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/stock_deduct.lua")
        ));
        log.info("Lua脚本加载成功");
    }

    /**
     * 执行库存扣减
     *
     * @param stockKey 库存Key，格式：seckill:stock:{goodsId}
     * @param quantity 扣减数量，默认为1
     * @return 1-扣减成功，0-库存不足，-1-库存Key不存在
     */
    public Long deductStock(String stockKey, Integer quantity) {
        try {
            List<String> keys = Collections.singletonList(stockKey);
            String quantityStr = quantity == null ? "1" : quantity.toString();
            Long result = stringRedisTemplate.execute(
                    stockDeductScript,
                    keys,
                    quantityStr
            );
            log.info("Lua脚本执行库存扣减，Key: {}, 数量: {}, 结果: {}",
                    stockKey, quantity, result);
            return result;
        } catch (Exception e) {
            log.error("Lua脚本执行失败，Key: {}", stockKey, e);
            return -1L;
        }
    }

    /**
     * 执行库存扣减（默认扣减1）
     *
     * @param stockKey 库存Key
     * @return 1-扣减成功，0-库存不足，-1-库存Key不存在
     */
    public Long deductStock(String stockKey) {
        return deductStock(stockKey, 1);
    }

    /**
     * 判断扣减结果
     *
     * @param result Lua脚本执行结果
     * @return true-扣减成功，false-扣减失败
     */
    public static boolean isDeductSuccess(Long result) {
        return result != null && result == 1L;
    }

    /**
     * 判断是否库存不足
     *
     * @param result Lua脚本执行结果
     * @return true-库存不足，false-其他情况
     */
    public static boolean isStockInsufficient(Long result) {
        return result != null && result == 0L;
    }

    /**
     * 判断是否Key不存在
     *
     * @param result Lua脚本执行结果
     * @return true-Key不存在，false-其他情况
     */
    public static boolean isKeyNotExist(Long result) {
        return result != null && result == -1L;
    }
}
