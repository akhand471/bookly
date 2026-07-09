package com.bookly.security;

import com.bookly.config.RateLimitConfig;
import com.bookly.config.RateLimitProperties;
import com.bookly.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Bucket4j-backed Redis rate limiter for authentication endpoints.
 *
 * <p>Intercepts {@code /api/v1/auth/login} and {@code /api/v1/auth/register}.
 * Each bucket is stored in Redis under the key {@code rate_limit:{endpoint}:{clientIp}},
 * preserving the same key format as the previous hand-rolled implementation.
 *
 * <p>Unlike the previous INCR+EXPIRE approach (two non-atomic Redis calls), Bucket4j
 * uses a single CAS (compare-and-swap) operation — eliminating the race condition where
 * a concurrent request could reset the window TTL on a non-first hit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private final ProxyManager<String> proxyManager;
    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals("/api/v1/auth/login") && !path.equals("/api/v1/auth/register");
    }

    @Override
    public void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path = request.getServletPath();
        String clientIp = clientIpResolver.resolve(request);
        boolean isLogin = path.contains("/login");

        RateLimitProperties.Endpoint config = isLogin
                ? rateLimitProperties.getLogin()
                : rateLimitProperties.getRegister();

        String endpoint = isLogin ? "login" : "register";
        String redisKey = "rate_limit:" + endpoint + ":" + clientIp;

        BucketConfiguration bucketConfig = RateLimitConfig.buildBucketConfig(config);
        Bucket bucket = proxyManager.builder().build(redisKey, () -> bucketConfig);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Token was not available — compute Retry-After from nanos remaining until refill
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        if (retryAfterSeconds <= 0) {
            retryAfterSeconds = config.getWindowSeconds();
        }

        log.warn("Rate limit exceeded for IP {} on {}", clientIp, path);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(429); // Too Many Requests
        response.setIntHeader("Retry-After", (int) retryAfterSeconds);

        ApiResponse<Object> apiResponse = ApiResponse.error(
                "Too many requests. Please try again in " + retryAfterSeconds + " seconds.");
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
