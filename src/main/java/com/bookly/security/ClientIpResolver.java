package com.bookly.security;

import com.bookly.config.TrustedProxyProperties;
import inet.ipaddr.IPAddressString;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves the real client IP address, honoring X-Forwarded-For only when the
 * immediate upstream (request.getRemoteAddr()) is a configured trusted proxy.
 *
 * <p>Parsing is done right-to-left through the X-Forwarded-For chain, skipping
 * IPs that belong to trusted proxies, to find the first non-proxy IP.
 *
 * <p>When trustedProxies is empty (the default), X-Forwarded-For is NEVER
 * trusted and remoteAddr is always returned — safe for direct-to-internet dev setups.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClientIpResolver {

    private final TrustedProxyProperties trustedProxyProperties;

    /**
     * Resolve the real client IP for the given request.
     *
     * @param request the incoming HTTP request
     * @return the resolved client IP string, never null
     */
    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        // If no proxies are configured, always trust the direct connection
        if (trustedProxyProperties.getTrustedProxies().isEmpty()) {
            return remoteAddr;
        }

        // Only honor X-Forwarded-For if the direct connection comes from a trusted proxy
        if (!isTrustedProxy(remoteAddr)) {
            return remoteAddr;
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor == null || xForwardedFor.isBlank()) {
            return remoteAddr;
        }

        // Parse right-to-left, skipping trusted proxy IPs, to find the real client
        String[] hops = xForwardedFor.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            String hop = hops[i].trim();
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }

        // All IPs in XFF were trusted proxies — fall back to remoteAddr
        log.warn("All X-Forwarded-For hops are trusted proxies; falling back to remoteAddr={}", remoteAddr);
        return remoteAddr;
    }

    /**
     * Returns true if the given IP matches any entry in the configured trusted-proxies list.
     * Supports both exact IP addresses and CIDR notation (e.g. 10.0.0.0/8).
     */
    boolean isTrustedProxy(String ip) {
        for (String proxy : trustedProxyProperties.getTrustedProxies()) {
            try {
                if (proxy.contains("/")) {
                    if (isInCidr(ip, proxy)) return true;
                } else {
                    if (proxy.equals(ip)) return true;
                }
            } catch (Exception e) {
                log.warn("Invalid trusted-proxy entry '{}': {}", proxy, e.getMessage());
            }
        }
        return false;
    }

    private boolean isInCidr(String ip, String cidr) {
        try {
            IPAddressString network = new IPAddressString(cidr);
            IPAddressString address = new IPAddressString(ip);
            if (!network.isValid() || !address.isValid()) {
                log.warn("Invalid CIDR '{}' or IP '{}' — skipping entry", cidr, ip);
                return false;
            }
            return network.toAddress().contains(address.toAddress());
        } catch (Exception e) {
            log.warn("Error checking CIDR '{}' for IP '{}': {}", cidr, ip, e.getMessage());
            return false;
        }
    }
}
