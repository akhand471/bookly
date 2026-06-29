package com.bookly.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private Endpoint login = new Endpoint(5, 900);    // 5 attempts per 15 min
    private Endpoint register = new Endpoint(3, 600); // 3 attempts per 10 min

    @Data
    public static class Endpoint {
        private int maxAttempts;
        private int windowSeconds;

        public Endpoint() {}

        public Endpoint(int maxAttempts, int windowSeconds) {
            this.maxAttempts = maxAttempts;
            this.windowSeconds = windowSeconds;
        }
    }
}
