package com.gzhu.equipment.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 登录频率限制 — Redis 实现滑动窗口
 * 每个IP每分钟最多5次登录尝试，锁定5分钟
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginRateLimiter {

    private final RedisTemplate<String, String> redisTemplate;

    private static final int MAX_ATTEMPTS = 20;  // 开发/测试环境放宽限制
    private static final int WINDOW_SECONDS = 60;
    private static final int LOCK_MINUTES = 5;

    /**
     * 检查是否允许登录尝试
     * @return true=允许，false=被限制
     */
    public boolean allowAttempt(String ip) {
        String lockKey = "login:lock:" + ip;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))) {
            log.warn("登录IP被锁定: ip={}", ip);
            return false;
        }

        String countKey = "login:count:" + ip;
        Long count = redisTemplate.opsForValue().increment(countKey);
        if (count == 1) {
            redisTemplate.expire(countKey, WINDOW_SECONDS, TimeUnit.SECONDS);
        }

        if (count != null && count > MAX_ATTEMPTS) {
            redisTemplate.opsForValue().set(lockKey, "locked", LOCK_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(countKey);
            log.warn("登录频率超限，已锁定: ip={}", ip);
            return false;
        }

        return true;
    }

    /** 登录成功后清除计数 */
    public void clearAttempt(String ip) {
        redisTemplate.delete("login:count:" + ip);
        redisTemplate.delete("login:lock:" + ip);
    }
}
