package com.bookly.security;

import com.bookly.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookly.dto.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Redis-backed sliding-window rate limiter for authentication endpoints.
 * Intercepts /api/v1/auth/login and /api/v1/auth/register.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String clientIp = getClientIp(request);

        RateLimitProperties.Endpoint config = path.contains("/login")
                ? rateLimitProperties.getLogin()
                : rateLimitProperties.getRegister();

        String redisKey = "rate_limit:" + (path.contains("/login") ? "login" : "register") + ":" + clientIp;

        Long currentCount = redisTemplate.opsForValue().increment(redisKey);
        if (currentCount != null && currentCount == 1) {
            // First request in window — set expiry
            redisTemplate.expire(redisKey, Duration.ofSeconds(config.getWindowSeconds()));
        }

        if (currentCount != null && currentCount > config.getMaxAttempts()) {
            Long ttl = redisTemplate.getExpire(redisKey);
            int retryAfter = ttl != null && ttl > 0 ? ttl.intValue() : config.getWindowSeconds();

            log.warn("Rate limit exceeded for IP {} on {}", clientIp, path);

            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setStatus(429); // Too Many Requests
            response.setIntHeader("Retry-After", retryAfter);

            ApiResponse<Object> apiResponse = ApiResponse.error(
                    "Too many requests. Please try again in " + retryAfter + " seconds.");
            response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
