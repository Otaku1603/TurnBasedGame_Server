package com.game.fwork.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 * 基于 HMAC-SHA256 算法生成和验证用户 Token
 * 作用：实现无状态的身份认证，客户端登录后获取 Token，后续请求只需携带 Token 即可识别身份
 */
@Component  // 让Spring管理这个类，可以在其他地方@Autowired注入
public class JwtUtil {

    /**
     * JWT密钥（从配置文件读取）
     */
    @Value("${jwt.secret}")
    private String secret;

    /**
     * Token过期时间（毫秒）
     */
    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 生成JWT Token
     * 将 userId 和 username 写入负载（Payload），并设置过期时间
     *
     * @param userId 用户ID
     * @param username 用户名
     * @return JWT Token字符串
     */
    public String generateToken(Long userId, String username) {
        // 当前时间
        Date now = new Date();
        // 过期时间 = 当前时间 + 过期时长
        Date expiryDate = new Date(now.getTime() + expiration);

        // 将密钥字符串转换为SecretKey对象
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        // 构建JWT Token
        return Jwts.builder()
                // 设置主题（通常放用户ID）
                .subject(userId.toString())
                // 添加自定义声明（Claim）
                .claim("username", username)  // 可以添加更多信息，如角色、权限等
                .claim("userId", userId)
                // 签发时间
                .issuedAt(now)
                // 过期时间
                .expiration(expiryDate)
                // 使用HS256算法签名
                .signWith(key, Jwts.SIG.HS256)
                // 生成最终的Token字符串
                .compact();
    }

    /**
     * 从Token中解析出用户ID
     *
     * @param token JWT Token
     * @return 用户ID，如果Token无效返回null
     */
    public Long getUserIdFromToken(String token) {
        try {
            // 创建密钥
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

            // 解析Token
            Claims claims = Jwts.parser()
                    .verifyWith(key)  // 验证签名
                    .build()
                    .parseSignedClaims(token)  // 解析Token
                    .getPayload();  // 获取负载（存储的数据）

            // 从负载中获取userId
            Object userIdObj = claims.get("userId");
            if (userIdObj instanceof Integer) {
                return ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof Long) {
                return (Long) userIdObj;
            }
            return null;
        } catch (Exception e) {
            // Token无效、过期或签名错误都会抛异常
            return null;
        }
    }

    /**
     * 从Token中解析出用户名
     *
     * @param token JWT Token
     * @return 用户名，如果Token无效返回null
     */
    public String getUsernameFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return claims.get("username", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 Token 是否合法
     * 1. 检查签名是否正确（防篡改）
     * 2. 检查是否过期
     *
     * @param token JWT Token
     * @return true=有效, false=无效
     */
    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

            // 尝试解析Token，如果能解析成功说明有效
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (ExpiredJwtException e) {
            // Token已过期
            System.out.println("Token已过期: " + e.getMessage());
            return false;
        } catch (UnsupportedJwtException e) {
            // 不支持的JWT格式
            System.out.println("不支持的JWT: " + e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            // JWT格式错误
            System.out.println("JWT格式错误: " + e.getMessage());
            return false;
        } catch (SecurityException e) {
            // 签名验证失败
            System.out.println("签名验证失败: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            // Token为空
            System.out.println("Token为空: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取Token的剩余有效时间（毫秒）
     *
     * @param token JWT Token
     * @return 剩余有效时间（毫秒），如果Token无效返回0
     */
    public long getExpirationTime(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date expiration = claims.getExpiration();
            Date now = new Date();
            return expiration.getTime() - now.getTime();
        } catch (Exception e) {
            return 0;
        }
    }
}
