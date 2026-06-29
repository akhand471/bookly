package com.bookly.service;

import com.bookly.entity.RefreshToken;
import com.bookly.entity.User;
import com.bookly.exception.UnauthorizedException;
import com.bookly.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    @Value("${app.jwt.refresh-expiration-ms}")
    private Long refreshExpirationMs;

    private final RefreshTokenRepository refreshTokenRepository;

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    /**
     * Create a refresh token for the given user and device.
     * If a token already exists for the same device, it is replaced.
     * If deviceFingerprint is null, all existing tokens for the user are deleted (legacy behavior).
     */
    @Transactional
    public RefreshToken createRefreshToken(User user, String deviceFingerprint, String userAgent) {
        if (deviceFingerprint != null) {
            // Replace only the token for this specific device
            refreshTokenRepository.deleteByUserAndDeviceFingerprint(user, deviceFingerprint);
        } else {
            // Fallback: no device ID → single-session behavior
            refreshTokenRepository.deleteByUser(user);
        }

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshExpirationMs))
                .deviceFingerprint(deviceFingerprint)
                .userAgent(userAgent)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    /**
     * Backwards-compatible overload for callers that don't pass device info.
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        return createRefreshToken(user, null, null);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new UnauthorizedException("Refresh token has expired. Please sign in again.");
        }
        return token;
    }

    /**
     * Revoke all sessions for a user ("sign out everywhere").
     */
    @Transactional
    public int deleteByUser(User user) {
        return refreshTokenRepository.deleteByUser(user);
    }
}
