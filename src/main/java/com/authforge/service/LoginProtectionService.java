package com.authforge.service;

import com.authforge.security.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginProtectionService {

    private final StringRedisTemplate redisTemplate;
    private final TokenHasher tokenHasher;

    @Value("${authforge.login.max-failures}")
    private int maxFailures;

    @Value("${authforge.login.failure-window}")
    private Duration failureWindow;

    @Value("${authforge.login.lock-duration}")
    private Duration lockDuration;

    public boolean isBlocked(String clientId, String email) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(blockedKey(clientId, email)));
        } catch (RuntimeException exception) {
            log.warn("Redis is unavailable; login lockout check is operating fail-open");
            return false;
        }
    }

    public void recordFailure(String clientId, String email) {
        try {
            String failuresKey = failuresKey(clientId, email);
            Long failures = redisTemplate.opsForValue().increment(failuresKey);
            if (failures != null && failures == 1) {
                redisTemplate.expire(failuresKey, failureWindow);
            }
            if (failures != null && failures >= maxFailures) {
                redisTemplate.opsForValue().set(blockedKey(clientId, email), "1", lockDuration);
                redisTemplate.delete(failuresKey);
            }
        } catch (RuntimeException exception) {
            log.warn("Redis is unavailable; failed login could not be recorded");
        }
    }

    public void recordSuccess(String clientId, String email) {
        try {
            redisTemplate.delete(failuresKey(clientId, email));
            redisTemplate.delete(blockedKey(clientId, email));
        } catch (RuntimeException exception) {
            log.warn("Redis is unavailable; login failure counters could not be cleared");
        }
    }

    private String failuresKey(String clientId, String email) {
        return "authforge:login:failures:" + identityHash(clientId, email);
    }

    private String blockedKey(String clientId, String email) {
        return "authforge:login:blocked:" + identityHash(clientId, email);
    }

    private String identityHash(String clientId, String email) {
        return tokenHasher.hash(clientId + ":" + email.toLowerCase(Locale.ROOT));
    }
}
