package io.github.turmony.seckillsystem.util;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;

/**
 * JWT工具类
 */
public class JwtUtil {

    /**
     * 密钥（生产环境建议放到配置文件）
     */
    private static final String SECRET_KEY = "seckill_jwt_secret_key_2024_spring_boot";

    /**
     * 过期时间：7天（毫秒）
     */
    private static final long EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    /**
     * 生成JWT Token
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT Token
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expireDate = new Date(now.getTime() + EXPIRATION);

        return Jwts.builder()
                .setSubject(username)                          // 主题（用户名）
                .claim("userId", userId)                        // 自定义字段：用户ID
                .claim("username", username)                    // 自定义字段：用户名
                .setIssuedAt(now)                              // 签发时间
                .setExpiration(expireDate)                     // 过期时间
                .signWith(SignatureAlgorithm.HS512, SECRET_KEY) // 签名算法
                .compact();
    }

    /**
     * 解析JWT Token
     * @param token JWT Token
     * @return Claims（包含用户信息）
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .setSigningKey(SECRET_KEY)
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从Token中获取用户ID
     * @param token JWT Token
     * @return 用户ID
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取用户名
     * @param token JWT Token
     * @return 用户名
     */
    public static String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        if (claims == null) {
            return null;
        }
        return claims.get("username", String.class);
    }

    /**
     * 验证Token是否有效
     * @param token JWT Token
     * @return 是否有效
     */
    public static boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                return false;
            }
            // 检查是否过期
            Date expiration = claims.getExpiration();
            return expiration.after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 测试方法
     */
    public static void main(String[] args) {
        // 生成Token
        String token = generateToken(1L, "testuser");
        System.out.println("生成的Token: " + token);

        // 解析Token
        Long userId = getUserIdFromToken(token);
        String username = getUsernameFromToken(token);
        System.out.println("用户ID: " + userId);
        System.out.println("用户名: " + username);

        // 验证Token
        boolean isValid = validateToken(token);
        System.out.println("Token是否有效: " + isValid);
    }
}