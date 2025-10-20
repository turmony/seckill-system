package io.github.turmony.seckillsystem.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    // 密钥（建议放在配置文件中）
    private static final String SECRET_KEY = "your-secret-key-at-least-256-bits-long-for-hs256-algorithm";

    // 过期时间：7天
    private static final long EXPIRATION_TIME = 7 * 24 * 60 * 60 * 1000;

    // 生成密钥
    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成Token
     */
    public static String generateToken(Long userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME);

        return Jwts.builder()
                .setSubject(username)
                .claim("userId", userId)
                .claim("username", username)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)  // ⭐ 修改这里
                .compact();
    }

    /**
     * 验证Token
     */
    public static boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()  // ⭐ 使用 parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parserBuilder()  // ⭐ 使用 parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取用户名
     */
    public static String getUsernameFromToken(String token) {
        Claims claims = Jwts.parserBuilder()  // ⭐ 使用 parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("username", String.class);
    }
}