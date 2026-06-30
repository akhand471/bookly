package com.bookly.security;

import com.bookly.config.TrustedProxyProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ClientIpResolver — no Spring context needed.
 */
class ClientIpResolverTest {

    private TrustedProxyProperties props;
    private ClientIpResolver resolver;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        props = new TrustedProxyProperties();
        resolver = new ClientIpResolver(props);
        request = mock(HttpServletRequest.class);
    }

    // ── No configured proxies ──────────────────────────────────────────────────

    @Test
    void noProxiesConfigured_noHeader_returnsRemoteAddr() {
        props.setTrustedProxies(List.of());
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void noProxiesConfigured_headerPresent_headerIsIgnored() {
        // Even if a header exists, we never trust it when no proxies are configured
        props.setTrustedProxies(List.of());
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.10");
    }

    // ── Untrusted direct connection ────────────────────────────────────────────

    @Test
    void proxiesConfigured_remoteAddrNotTrusted_headerIgnored() {
        // remoteAddr is a random client IP, not our LB — ignore the header
        props.setTrustedProxies(List.of("10.0.0.1"));
        when(request.getRemoteAddr()).thenReturn("203.0.113.99");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.99");
    }

    // ── Single trusted hop ─────────────────────────────────────────────────────

    @Test
    void singleTrustedProxy_extractsClientIpFromHeader() {
        props.setTrustedProxies(List.of("10.0.0.1"));
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.50");
    }

    @Test
    void singleTrustedProxy_headerAbsent_returnsRemoteAddr() {
        props.setTrustedProxies(List.of("10.0.0.1"));
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.1");
    }

    // ── Chained trusted hops ───────────────────────────────────────────────────

    @Test
    void chainedTrustedProxies_parsesRightToLeft_returnsFirstNonTrusted() {
        // chain: client → proxy-a(10.0.0.2) → proxy-b(10.0.0.1) → app
        props.setTrustedProxies(List.of("10.0.0.1", "10.0.0.2"));
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.77, 10.0.0.2");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.77");
    }

    @Test
    void chainedTrustedProxies_allHopsAreTrusted_fallsBackToRemoteAddr() {
        props.setTrustedProxies(List.of("10.0.0.1", "10.0.0.2"));
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.2");

        // All IPs in XFF are trusted proxies → fall back to remoteAddr
        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.1");
    }

    // ── CIDR matching ──────────────────────────────────────────────────────────

    @Test
    void cidrProxy_remoteAddrInRange_headerHonored() {
        props.setTrustedProxies(List.of("10.0.0.0/8"));
        when(request.getRemoteAddr()).thenReturn("10.42.1.99");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void cidrProxy_remoteAddrOutsideRange_headerIgnored() {
        props.setTrustedProxies(List.of("10.0.0.0/8"));
        when(request.getRemoteAddr()).thenReturn("192.168.1.1");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4");

        assertThat(resolver.resolve(request)).isEqualTo("192.168.1.1");
    }

    // ── isTrustedProxy helper ──────────────────────────────────────────────────

    @Test
    void isTrustedProxy_exactMatch_returnsTrue() {
        props.setTrustedProxies(List.of("10.0.0.1"));
        assertThat(resolver.isTrustedProxy("10.0.0.1")).isTrue();
    }

    @Test
    void isTrustedProxy_noMatch_returnsFalse() {
        props.setTrustedProxies(List.of("10.0.0.1"));
        assertThat(resolver.isTrustedProxy("10.0.0.2")).isFalse();
    }

    @Test
    void isTrustedProxy_cidrMatch_returnsTrue() {
        props.setTrustedProxies(List.of("172.16.0.0/12"));
        assertThat(resolver.isTrustedProxy("172.20.0.1")).isTrue();
    }
}
