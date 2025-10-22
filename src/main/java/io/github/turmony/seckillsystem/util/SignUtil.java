package io.github.turmony.seckillsystem.util;

import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.TreeMap;

/**
 * 接口签名工具类
 * 用于生成和验证接口签名，防止接口被恶意调用
 *
 * @author turmony
 */
@Slf4j
public class SignUtil {

    /**
     * 签名密钥（生产环境应该配置在配置文件中）
     */
    private static final String SIGN_SALT = "seckill_system_secret_key_2024";

    /**
     * 签名有效期（毫秒）- 5分钟
     */
    private static final long SIGN_VALID_TIME = 5 * 60 * 1000;

    /**
     * 生成签名
     * 规则: MD5(所有参数按key排序拼接 + timestamp + salt)
     *
     * @param params 请求参数
     * @param timestamp 时间戳
     * @return 签名字符串
     */
    public static String generateSign(Map<String, String> params, Long timestamp) {
        // 使用TreeMap自动按key排序
        TreeMap<String, String> sortedParams = new TreeMap<>(params);

        // 构建待签名字符串
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            // 排除sign和timestamp参数本身
            if (!"sign".equals(entry.getKey()) && !"timestamp".equals(entry.getKey())) {
                sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
            }
        }

        // 添加时间戳和盐值
        sb.append("timestamp=").append(timestamp).append("&");
        sb.append("salt=").append(SIGN_SALT);

        // MD5加密
        String signStr = sb.toString();
        String sign = DigestUtil.md5Hex(signStr);

        log.debug("签名原文: {}", signStr);
        log.debug("生成签名: {}", sign);

        return sign;
    }

    /**
     * 验证签名
     *
     * @param params 请求参数（包含sign和timestamp）
     * @return true-验证通过, false-验证失败
     */
    public static boolean verifySign(Map<String, String> params) {
        // 1. 检查必要参数
        String sign = params.get("sign");
        String timestampStr = params.get("timestamp");

        if (sign == null || sign.isEmpty()) {
            log.warn("签名验证失败: 缺少签名参数");
            return false;
        }

        if (timestampStr == null || timestampStr.isEmpty()) {
            log.warn("签名验证失败: 缺少时间戳参数");
            return false;
        }

        // 2. 验证时间戳是否过期
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampStr);
        } catch (NumberFormatException e) {
            log.warn("签名验证失败: 时间戳格式错误");
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - timestamp) > SIGN_VALID_TIME) {
            log.warn("签名验证失败: 请求已过期, 请求时间={}, 当前时间={}, 差值={}ms",
                    timestamp, currentTime, Math.abs(currentTime - timestamp));
            return false;
        }

        // 3. 重新生成签名并比对
        String generatedSign = generateSign(params, timestamp);
        boolean result = sign.equals(generatedSign);

        if (!result) {
            log.warn("签名验证失败: 签名不匹配, 客户端签名={}, 服务端签名={}", sign, generatedSign);
        } else {
            log.debug("签名验证成功");
        }

        return result;
    }

    /**
     * 生成签名（简化版本，适用于简单参数）
     *
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @param timestamp 时间戳
     * @return 签名字符串
     */
    public static String generateSimpleSign(Long userId, Long goodsId, Long timestamp) {
        StringBuilder sb = new StringBuilder();
        sb.append("goodsId=").append(goodsId).append("&");
        sb.append("userId=").append(userId).append("&");
        sb.append("timestamp=").append(timestamp).append("&");
        sb.append("salt=").append(SIGN_SALT);

        return DigestUtil.md5Hex(sb.toString());
    }

    /**
     * 验证简单签名
     *
     * @param sign 待验证的签名
     * @param userId 用户ID
     * @param goodsId 商品ID
     * @param timestamp 时间戳
     * @return true-验证通过, false-验证失败
     */
    public static boolean verifySimpleSign(String sign, Long userId, Long goodsId, Long timestamp) {
        // 验证时间戳
        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - timestamp) > SIGN_VALID_TIME) {
            log.warn("签名验证失败: 请求已过期");
            return false;
        }

        // 重新生成签名并比对
        String generatedSign = generateSimpleSign(userId, goodsId, timestamp);
        return sign.equals(generatedSign);
    }

    /**
     * 获取当前时间戳
     *
     * @return 当前时间戳（毫秒）
     */
    public static long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}
