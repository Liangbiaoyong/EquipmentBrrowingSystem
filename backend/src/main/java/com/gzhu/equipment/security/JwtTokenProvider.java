package com.gzhu.equipment.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * JWT Token 工具 — 生成、解析、校验
 *
 * Token Payload 结构：
 * - sub: 用户ID
 * - username: 登录名
 * - roles: 角色列表（如 ROLE_STUDENT, ROLE_TEACHER）
 * - userType: 用户类型编码
 * - iat: 签发时间
 * - exp: 过期时间
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {
        // HMAC-SHA512 要求密钥长度 ≥ 512 bits (64 bytes)；启动时校验
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 64) {
            throw new IllegalArgumentException(
                    "jwt.secret 长度不足: 当前 " + keyBytes.length + " 字节，HMAC-SHA512 要求至少 64 字节(512 bits)。" +
                    " 请在配置文件中设置足够长的密钥。");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * 生成JWT Token
     */
    public String generateToken(Long userId, String username, Integer userType, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .claim("userType", userType)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * 从Token中解析Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 校验Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.debug("不支持的JWT: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.debug("JWT格式错误: {}", e.getMessage());
        } catch (SignatureException e) {
            log.debug("JWT签名无效: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT参数非法: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    /**
     * 从Token中获取角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return parseToken(token).get("roles", List.class);
    }

    /**
     * 从Token中获取用户类型
     */
    public Integer getUserType(String token) {
        return parseToken(token).get("userType", Integer.class);
    }

    /**
     * 获取Token有效期（毫秒）
     */
    public long getExpirationMs() {
        return expirationMs;
    }
}
