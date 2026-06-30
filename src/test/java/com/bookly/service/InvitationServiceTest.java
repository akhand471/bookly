package com.bookly.service;

import com.bookly.dto.AcceptInvitationRequest;
import com.bookly.dto.CreateInvitationRequest;
import com.bookly.entity.*;
import com.bookly.exception.BadRequestException;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.repository.BusinessRepository;
import com.bookly.repository.InvitationTokenRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock private InvitationTokenRepository invitationTokenRepository;
    @Mock private BusinessRepository businessRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditService auditService;

    @InjectMocks
    private InvitationService invitationService;

    private Business business;
    private UUID businessId;
    private UUID inviterId;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        inviterId = UUID.randomUUID();
        business = new Business();
        business.setId(businessId);
        business.setName("Test Salon");
        business.setSubdomain("testsalon");
    }

    // ── createInvite ──────────────────────────────────────────────────────────

    @Test
    void createInvite_happyPath_savesTokenAndReturnsRawToken() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.existsByEmail("staff@example.com")).thenReturn(false);
        when(invitationTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        String rawToken = invitationService.createInvite(businessId, inviterId,
                new CreateInvitationRequest("staff@example.com", null));

        assertThat(rawToken).isNotBlank();
        ArgumentCaptor<InvitationToken> captor = ArgumentCaptor.forClass(InvitationToken.class);
        verify(invitationTokenRepository).save(captor.capture());
        InvitationToken saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("staff@example.com");
        assertThat(saved.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(saved.getTokenHash()).isNotBlank().isNotEqualTo(rawToken);
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    void createInvite_withExplicitRole_setsRole() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.existsByEmail("manager@example.com")).thenReturn(false);
        when(invitationTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        invitationService.createInvite(businessId, inviterId,
                new CreateInvitationRequest("manager@example.com", "EMPLOYEE"));

        ArgumentCaptor<InvitationToken> captor = ArgumentCaptor.forClass(InvitationToken.class);
        verify(invitationTokenRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.EMPLOYEE);
    }

    @Test
    void createInvite_emailAlreadyExists_throwsConflict() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.existsByEmail("exists@example.com")).thenReturn(true);

        assertThatThrownBy(() -> invitationService.createInvite(businessId, inviterId,
                new CreateInvitationRequest("exists@example.com", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createInvite_businessNotFound_throwsNotFound() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.createInvite(businessId, inviterId,
                new CreateInvitationRequest("staff@example.com", null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createInvite_escalationToOwner_throwsBadRequest() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);

        assertThatThrownBy(() -> invitationService.createInvite(businessId, inviterId,
                new CreateInvitationRequest("user@example.com", "BUSINESS_OWNER")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot invite users with role");
    }

    @Test
    void createInvite_escalationToSuperAdmin_throwsBadRequest() {
        when(businessRepository.findById(businessId)).thenReturn(Optional.of(business));
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);

        assertThatThrownBy(() -> invitationService.createInvite(businessId, inviterId,
                new CreateInvitationRequest("user@example.com", "SUPER_ADMIN")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot invite users with role");
    }

    // ── acceptInvite ──────────────────────────────────────────────────────────

    @Test
    void acceptInvite_happyPath_createsUserAndMarksTokenAccepted() {
        String rawToken = UUID.randomUUID().toString();
        InvitationToken invite = buildInvite(rawToken, false, OffsetDateTime.now().plusHours(72));
        when(invitationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));
        when(userRepository.existsByEmail("staff@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(invitationTokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        User result = invitationService.acceptInvite(buildAcceptRequest(rawToken));

        assertThat(result.getEmail()).isEqualTo("staff@example.com");
        assertThat(result.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(result.getBusiness().getId()).isEqualTo(businessId);
        assertThat(invite.isAccepted()).isTrue();
    }

    @Test
    void acceptInvite_expiredToken_throwsBadRequest() {
        String rawToken = UUID.randomUUID().toString();
        InvitationToken invite = buildInvite(rawToken, false, OffsetDateTime.now().minusHours(1));
        when(invitationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> invitationService.acceptInvite(buildAcceptRequest(rawToken)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void acceptInvite_alreadyAcceptedToken_throwsBadRequest() {
        String rawToken = UUID.randomUUID().toString();
        InvitationToken invite = buildInvite(rawToken, true, OffsetDateTime.now().plusHours(72));
        when(invitationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));

        assertThatThrownBy(() -> invitationService.acceptInvite(buildAcceptRequest(rawToken)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already been accepted");
    }

    @Test
    void acceptInvite_invalidToken_throwsBadRequest() {
        when(invitationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.acceptInvite(buildAcceptRequest("bad-token")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void acceptInvite_emailAlreadyTaken_throwsConflict() {
        String rawToken = UUID.randomUUID().toString();
        InvitationToken invite = buildInvite(rawToken, false, OffsetDateTime.now().plusHours(72));
        when(invitationTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(invite));
        when(userRepository.existsByEmail("staff@example.com")).thenReturn(true);

        assertThatThrownBy(() -> invitationService.acceptInvite(buildAcceptRequest(rawToken)))
                .isInstanceOf(ConflictException.class);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private InvitationToken buildInvite(String rawToken, boolean accepted, OffsetDateTime expiresAt) {
        // SHA-256 the raw token the same way the service does
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            String tokenHash = java.util.HexFormat.of().formatHex(hash);

            return InvitationToken.builder()
                    .id(UUID.randomUUID())
                    .email("staff@example.com")
                    .business(business)
                    .role(Role.EMPLOYEE)
                    .tokenHash(tokenHash)
                    .expiresAt(expiresAt)
                    .accepted(accepted)
                    .build();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private AcceptInvitationRequest buildAcceptRequest(String token) {
        AcceptInvitationRequest req = new AcceptInvitationRequest();
        req.setToken(token);
        req.setPassword("Password123!");
        req.setFirstName("Jane");
        req.setLastName("Staff");
        return req;
    }
}
