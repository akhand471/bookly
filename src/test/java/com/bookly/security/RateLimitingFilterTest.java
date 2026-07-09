package com.bookly.security;

import com.bookly.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder;
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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitingFilterTest {

    @Mock private ProxyManager<String> proxyManager;
    @Mock private RemoteBucketBuilder<String> remoteBucketBuilder;
    @Mock private BucketProxy bucketProxy;            // RemoteBucketBuilder.build() returns BucketProxy
    @Mock private RateLimitProperties rateLimitProperties;
    @Mock private ObjectMapper objectMapper;
    @Mock private ClientIpResolver clientIpResolver;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private RateLimitingFilter filter;

    private RateLimitProperties.Endpoint loginConfig;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        loginConfig = new RateLimitProperties.Endpoint(5, 900);
        when(rateLimitProperties.getLogin()).thenReturn(loginConfig);
        when(clientIpResolver.resolve(request)).thenReturn("10.0.0.1");
        when(request.getServletPath()).thenReturn("/api/v1/auth/login");
        when(proxyManager.builder()).thenReturn(remoteBucketBuilder);
        // Specify the Supplier<BucketConfiguration> overload explicitly to resolve ambiguity
        when(remoteBucketBuilder.build(anyString(), any(Supplier.class))).thenReturn(bucketProxy);
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    void underThreshold_requestPassesThrough() throws Exception {
        // 3 tokens remaining after consumption — well under the limit of 5
        when(bucketProxy.tryConsumeAndReturnRemaining(1))
            .thenReturn(ConsumptionProbe.consumed(3, 0));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void atThreshold_lastToken_requestPassesThrough() throws Exception {
        // Consuming the very last token — still isConsumed=true, chain proceeds
        when(bucketProxy.tryConsumeAndReturnRemaining(1))
            .thenReturn(ConsumptionProbe.consumed(0, 0));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    // ── Rate-limited responses ──────────────────────────────────────────────────

    @Test
    void overThreshold_returns429AndDoesNotCallChain() throws Exception {
        long waitNanos = TimeUnit.SECONDS.toNanos(300);
        when(bucketProxy.tryConsumeAndReturnRemaining(1))
            .thenReturn(ConsumptionProbe.rejected(0, waitNanos, 0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"error\":\"rate limited\"}");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(429);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void overThreshold_retryAfterHeaderSetFromNanosToWait() throws Exception {
        // 300 seconds until refill
        long waitNanos = TimeUnit.SECONDS.toNanos(300);
        when(bucketProxy.tryConsumeAndReturnRemaining(1))
            .thenReturn(ConsumptionProbe.rejected(0, waitNanos, 0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setIntHeader("Retry-After", 300);
    }

    @Test
    void overThreshold_retryAfterFallsBackToWindowSeconds_whenNanosToWaitIsZero() throws Exception {
        // nanosToWaitForRefill == 0 → fall back to configured windowSeconds (900)
        when(bucketProxy.tryConsumeAndReturnRemaining(1))
            .thenReturn(ConsumptionProbe.rejected(0, 0, 0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setIntHeader("Retry-After", 900);
    }

    // ── Redis key format ────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void redisKey_usesCorrectPrefixAndIp() throws Exception {
        when(bucketProxy.tryConsumeAndReturnRemaining(1))
            .thenReturn(ConsumptionProbe.consumed(3, 0));

        filter.doFilterInternal(request, response, filterChain);

        // Key must preserve: rate_limit:{endpoint}:{ip}
        verify(remoteBucketBuilder).build(eq("rate_limit:login:10.0.0.1"), any(Supplier.class));
    }

    // ── shouldNotFilter ─────────────────────────────────────────────────────────

    @Test
    void nonRateLimitedEndpoint_filterSkipped() {
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
