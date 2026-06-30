package com.bookly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration for trusted upstream proxies (load balancers, ingress controllers).
 * Only proxies listed here are allowed to set the X-Forwarded-For header.
 * Leave empty in dev; set to your LB/ingress CIDR in production.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.security")
public class TrustedProxyProperties {

    /**
     * List of trusted proxy IP addresses or CIDR blocks.
     * Example: ["10.0.0.0/8", "172.16.0.0/12"]
     * Empty by default — safe for dev (no header trusted).
     */
    private List<String> trustedProxies = List.of();
}
