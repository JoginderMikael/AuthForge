package com.authforge.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/auth/login",
            "/auth/register",
            "/auth/refresh-token",
            "/auth/oauth2/exchange",
            "/api/clients/register",
            "/oauth2/token");

    private final StringRedisTemplate redisTemplate;
    private final TokenHasher tokenHasher;

    @Value("${authforge.rate-limit.max-requests}")
    private int maxRequests;

    @Value("${authforge.rate-limit.window}")
    private Duration window;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String identity = request.getRemoteAddr() + ":" + request.getRequestURI();
            String key = "authforge:rate:" + tokenHasher.hash(identity);
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, window);
            }
            if (count != null && count > maxRequests) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"message\":\"Too many requests\",\"success\":false}");
                return;
            }
        } catch (RuntimeException exception) {
            log.warn("Redis is unavailable; request rate limit is operating fail-open");
        }
        filterChain.doFilter(request, response);
    }
}
