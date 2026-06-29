package com.bookly.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtUtils {

    private static final String DEV_DEFAULT_SECRET = "dGhpcyBpcyBhIHRlc3Qtb25seSBkZWZhdWx0IHNlY3JldCBrZXkgZm9yIGxvY2FsIGRldiBvbmx5!!!";

    private final SecretKey key;
    private final long jwtExpirationMs;
    private final String jwtSecret;
    private final Environment environment;

    public JwtUtils(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.jwt.expiration-ms}") long jwtExpirationMs,
            Environment environment) {
        this.jwtSecret = jwtSecret;
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        this.jwtExpirationMs = jwtExpirationMs;
        this.environment = environment;
    }

    @PostConstruct
    void validateSecret() {
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isDev = activeProfiles.length == 0
                || Arrays.stream(activeProfiles).anyMatch(p -> p.equals("dev") || p.equals("test"));
        if (!isDev && DEV_DEFAULT_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                "FATAL: JWT signing secret is the committed default. "
                + "Set the JWT_SECRET environment variable before starting in production. "
                + "Generate one with: openssl rand -base64 64");
        }
    }

    public String generateToken(CustomUserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userDetails.getId().toString());
        claims.put("businessId", userDetails.getBusinessId() != null ? userDetails.getBusinessId().toString() : null);
        claims.put("role", userDetails.getAuthorities().stream().findFirst().map(a -> a.getAuthority()).orElse("ROLE_CUSTOMER"));

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    /**
     * Extract a named claim value from a validated JWT token.
     * Returns null if the claim is absent or null.
     */
    public String getClaimFromToken(String token, String claimName) {
        Object value = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get(claimName);
        return value != null ? value.toString() : null;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getClass().getSimpleName());
        }
        return false;
    }
}
