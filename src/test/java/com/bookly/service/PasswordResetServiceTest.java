package com.bookly.service;

import com.bookly.entity.Business;
import com.bookly.entity.PasswordResetToken;
import com.bookly.entity.Role;
import com.bookly.entity.User;
import com.bookly.exception.BadRequestException;
import com.bookly.repository.PasswordResetTokenRepository;
import com.bookly.repository.RefreshTokenRepository;
import com.bookly.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock private PasswordResetTokenRepository resetTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User user;

    @BeforeEach
    void setUp() {
        Business business = new Business();
        business.setId(UUID.randomUUID());

        user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("old_hash")
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .isEnabled(true)
                .provider("LOCAL")
                .build();
    }

    // ── requestReset ──────────────────────────────────────────────────────────

    @Test
    void requestReset_knownEmail_savesTokenAndReturnsRawToken() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(resetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String rawToken = passwordResetService.requestReset("user@example.com");

        assertThat(rawToken).isNotBlank();
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(resetTokenRepository).save(captor.capture());
        PasswordResetToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getTokenHash()).isNotBlank().isNotEqualTo(rawToken);
        assertThat(saved.getExpiryDate()).isAfter(OffsetDateTime.now());
    }

    @Test
    void requestReset_unknownEmail_returnsNullWithoutThrowingOrRevealingExistence() {
        // Anti-enumeration: always returns quietly even if email not found
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        String result = passwordResetService.requestReset("ghost@example.com");

        assertThat(result).isNull();
        verifyNoInteractions(resetTokenRepository);
    }

    // ── executeReset ──────────────────────────────────────────────────────────

    @Test
    void executeReset_validToken_updatesPasswordAndMarksTokenUsed() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = buildToken(rawToken, false, OffsetDateTime.now().plusHours(1));

        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("new_hash");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(resetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        passwordResetService.executeReset(rawToken, "NewPassword123!");

        assertThat(user.getPasswordHash()).isEqualTo("new_hash");
        assertThat(resetToken.isUsed()).isTrue();
        verify(userRepository).save(user);
        verify(resetTokenRepository).save(resetToken);
    }

    @Test
    void executeReset_expiredToken_throwsBadRequest() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = buildToken(rawToken, false, OffsetDateTime.now().minusMinutes(1));

        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> passwordResetService.executeReset(rawToken, "NewPassword123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void executeReset_alreadyUsedToken_throwsBadRequest() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = buildToken(rawToken, true, OffsetDateTime.now().plusHours(1));

        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));

        assertThatThrownBy(() -> passwordResetService.executeReset(rawToken, "NewPassword123!"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void executeReset_invalidToken_throwsBadRequest() {
        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.executeReset("bad-token", "NewPassword123!"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void executeReset_logsAuditEvent() {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = buildToken(rawToken, false, OffsetDateTime.now().plusHours(1));

        when(resetTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(resetTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        passwordResetService.executeReset(rawToken, "NewPassword123!");

        verify(auditService).log(any(), any(), any(), any(), any(), any());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private PasswordResetToken buildToken(String rawToken, boolean used, OffsetDateTime expiryDate) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String tokenHash = java.util.HexFormat.of().formatHex(hash);

            return PasswordResetToken.builder()
                    .id(UUID.randomUUID())
                    .user(user)
                    .tokenHash(tokenHash)
                    .expiryDate(expiryDate)
                    .used(used)
                    .build();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
