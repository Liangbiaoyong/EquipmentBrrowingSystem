package com.gzhu.equipment.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Token 黑名单 — Redis实现，用于登出/角色变更后立刻失效旧Token
 *
 * 使用方式：
 * - POST /auth/logout → 将当前Token加入黑名单，过期时间=Token剩余有效期
 * - JwtAuthenticationFilter 在 validateToken 之前先查黑名单
 * - 管理员修改用户角色后，将该用户所有Token加入黑名单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    /**
     * 将Token加入黑名单
     * @param token JWT token
     * @param ttlSeconds 黑名单有效期（秒，建议用Token剩余有效时间）
     */
    public void add(String token, long ttlSeconds) {
        String key = BLACKLIST_PREFIX + token.substring(Math.max(0, token.length() - 20));
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        log.info("Token已加入黑名单，有效期={}s", ttlSeconds);
    }

    /**
     * 检查Token是否在黑名单中
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) return false;
        String key = BLACKLIST_PREFIX + token.substring(Math.max(0, token.length() - 20));
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
