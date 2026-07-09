package com.bookly.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

import java.time.Duration;

/**
 * Configures a Bucket4j {@link ProxyManager} backed by Lettuce (the Redis client
 * already used by spring-boot-starter-data-redis). No extra Redis client is added.
 */
@Configuration
public class RateLimitConfig {

    /**
     * Builds the Lettuce-backed ProxyManager used by {@link com.bookly.security.RateLimitingFilter}.
     * A dedicated Lettuce connection using a mixed codec (String keys, byte[] values) is created
     * so it does not interfere with the StringRedisTemplate connection pool.
     */
    @Bean
    public ProxyManager<String> rateLimitProxyManager(LettuceConnectionFactory lettuceConnectionFactory) {
        io.lettuce.core.AbstractRedisClient client = lettuceConnectionFactory.getNativeClient();
        if (!(client instanceof RedisClient redisClient)) {
            throw new IllegalStateException(
                "Bucket4j rate limiting requires a standalone Redis client, but found: "
                    + client.getClass().getName()
                    + ". Cluster mode is not supported in this configuration.");
        }

        StatefulRedisConnection<String, byte[]> connection =
            redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(connection)
            .withExpirationStrategy(
                // Keep bucket state in Redis for at least the longest configured window (login = 900s).
                // Bucket4j will extend TTL proportionally for longer configurations.
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(900)))
            .build();
    }

    /**
     * Builds a {@link BucketConfiguration} from an endpoint rate-limit config.
     * Uses an intervally refill: the full quota is atomically restored at the end of each window,
     * matching the previous fixed-window behavior.
     *
     * @param config endpoint-specific limits from {@link RateLimitProperties}
     */
    public static BucketConfiguration buildBucketConfig(RateLimitProperties.Endpoint config) {
        return BucketConfiguration.builder()
            .addLimit(Bandwidth.classic(
                config.getMaxAttempts(),
                Refill.intervally(
                    config.getMaxAttempts(),
                    Duration.ofSeconds(config.getWindowSeconds())
                )
            ))
            .build();
    }
}
