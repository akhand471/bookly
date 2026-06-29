package com.bookly.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

/**
 * Custom health check for Redis connectivity.
 * Reports DOWN if Redis is unreachable — critical for rate limiting and sessions.
 */
@Component
public class RedisHealthIndicator implements HealthIndicator {

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Override
    public Health health() {
        try {
            String pong = redisConnectionFactory.getConnection().ping();
            if ("PONG".equals(pong)) {
                return Health.up().withDetail("redis", "Connected").build();
            }
            return Health.down().withDetail("redis", "Unexpected response: " + pong).build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("redis", "Connection failed")
                    .withException(e)
                    .build();
        }
    }
}
