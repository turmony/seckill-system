package io.github.turmony.seckillsystem.util;


import cn.hutool.crypto.digest.DigestUtil;

/**
 * MD5加密工具类
 */
public class MD5Util {

    /**
     * 盐值（固定，用于加密）
     */
    private static final String SALT = "seckill@2024#salt";

    /**
     * MD5加密（带盐值）
     * @param password 原始密码
     * @return 加密后的密码
     */
    public static String encrypt(String password) {
        // 使用Hutool的MD5加密：MD5(password + salt)
        return DigestUtil.md5Hex(password + SALT);
    }

    /**
     * 验证密码
     * @param inputPassword 用户输入的密码
     * @param dbPassword 数据库存储的加密密码
     * @return 是否匹配
     */
    public static boolean verify(String inputPassword, String dbPassword) {
        String encrypted = encrypt(inputPassword);
        return encrypted.equals(dbPassword);
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        String password = "123456";
        String encrypted = encrypt(password);
        System.out.println("原始密码: " + password);
        System.out.println("加密后: " + encrypted);
        System.out.println("验证结果: " + verify(password, encrypted));
    }
}
