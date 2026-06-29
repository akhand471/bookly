package com.bookly.service;

import com.bookly.entity.AuditEventType;
import com.bookly.entity.PasswordResetToken;
import com.bookly.entity.User;
import com.bookly.exception.BadRequestException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.repository.PasswordResetTokenRepository;
import com.bookly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final PasswordResetTokenRepository resetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final int TOKEN_VALIDITY_HOURS = 1;

    /**
     * Generate a password reset token for the given email.
     * Always returns success to prevent email enumeration attacks.
     */
    @Transactional
    public String requestReset(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Log but don't reveal that the email doesn't exist
            log.info("Password reset requested for non-existent email");
            return null;
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiryDate(OffsetDateTime.now().plusHours(TOKEN_VALIDITY_HOURS))
                .build();
        resetTokenRepository.save(resetToken);

        auditService.log(AuditEventType.PASSWORD_RESET_REQUEST, user.getId(), user.getId(),
                user.getBusiness() != null ? user.getBusiness().getId() : null,
                null, Map.of("email", email));

        // In production, send via email provider. For now, log the link.
        log.info("Password reset link: /api/v1/auth/reset-password?token={}", rawToken);

        return rawToken;
    }

    /**
     * Execute a password reset using a valid token.
     */
    @Transactional
    public void executeReset(String rawToken, String newPassword) {
        String tokenHash = sha256(rawToken);
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        if (resetToken.isUsed()) {
            throw new BadRequestException("This password reset token has already been used");
        }

        if (resetToken.getExpiryDate().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Password reset token has expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        resetTokenRepository.save(resetToken);

        auditService.log(AuditEventType.PASSWORD_RESET_COMPLETE, user.getId(), user.getId(),
                user.getBusiness() != null ? user.getBusiness().getId() : null,
                null, Map.of("email", user.getEmail()));
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
