package com.bookly.security;

import com.bookly.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitingFilterTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RateLimitProperties rateLimitProperties;
    @Mock private ObjectMapper objectMapper;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private RateLimitingFilter filter;

    private RateLimitProperties.Endpoint loginConfig;

    @BeforeEach
    void setUp() {
        loginConfig = new RateLimitProperties.Endpoint(5, 900);
        when(rateLimitProperties.getLogin()).thenReturn(loginConfig);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(clientIpResolver.resolve(request)).thenReturn("10.0.0.1");
        when(request.getServletPath()).thenReturn("/api/v1/auth/login");
    }

    @Test
    void underThreshold_requestPassesThrough() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(3L);   // 3 < 5

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void atThreshold_requestPassesThrough() throws Exception {
        // count == maxAttempts (5 == 5) — boundary: still allowed
        when(valueOps.increment(anyString())).thenReturn(5L);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void overThreshold_returns429AndDoesNotCallChain() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(6L);   // 6 > 5
        when(redisTemplate.getExpire(anyString())).thenReturn(42L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"rate limited\"}");

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void overThreshold_retryAfterHeaderSetFromRedisTtl() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(6L);
        when(redisTemplate.getExpire(anyString())).thenReturn(300L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setIntHeader("Retry-After", 300);
    }

    @Test
    void overThreshold_retryAfterFallsBackToWindowSeconds_whenTtlNegative() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(6L);
        when(redisTemplate.getExpire(anyString())).thenReturn(-1L);  // key missing TTL
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        // Falls back to configured windowSeconds (900)
        verify(response).setIntHeader("Retry-After", 900);
    }

    @Test
    void firstRequest_setsRedisTtlOnKey() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(1L);  // first hit

        filter.doFilterInternal(request, response, filterChain);

        verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(900)));
    }

    @Test
    void subsequentRequest_doesNotResetTtl() throws Exception {
        when(valueOps.increment(anyString())).thenReturn(2L);  // not first hit

        filter.doFilterInternal(request, response, filterChain);

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    void nonRateLimitedEndpoint_filterSkipped() throws Exception {
        // shouldNotFilter returns true for paths outside /login and /register
        assertThat(filter.shouldNotFilter(mockRequestWithPath("/api/v1/auth/me"))).isTrue();
        assertThat(filter.shouldNotFilter(mockRequestWithPath("/api/v1/auth/login"))).isFalse();
        assertThat(filter.shouldNotFilter(mockRequestWithPath("/api/v1/auth/register"))).isFalse();
    }

    private HttpServletRequest mockRequestWithPath(String path) {
        HttpServletRequest r = mock(HttpServletRequest.class);
        when(r.getServletPath()).thenReturn(path);
        return r;
    }
}
