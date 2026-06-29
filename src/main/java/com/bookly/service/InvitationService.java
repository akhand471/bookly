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
public class InvitationService {

    private final InvitationTokenRepository invitationTokenRepository;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    private static final int INVITE_VALIDITY_HOURS = 72;

    /**
     * Create an invitation for an employee to join a business.
     * Only callable by business owners.
     */
    @Transactional
    public String createInvite(UUID businessId, UUID inviterId, CreateInvitationRequest request) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("A user with this email already exists");
        }

        Role role = Role.EMPLOYEE;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                role = Role.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role: " + request.getRole());
            }
            // Prevent privilege escalation
            if (role == Role.SUPER_ADMIN || role == Role.BUSINESS_OWNER) {
                throw new BadRequestException("Cannot invite users with role: " + role);
            }
        }

        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256(rawToken);

        InvitationToken invite = InvitationToken.builder()
                .email(request.getEmail())
                .business(business)
                .role(role)
                .tokenHash(tokenHash)
                .expiresAt(OffsetDateTime.now().plusHours(INVITE_VALIDITY_HOURS))
                .build();
        invitationTokenRepository.save(invite);

        auditService.log(AuditEventType.INVITE_CREATED, inviterId, null,
                businessId, null,
                Map.of("invitedEmail", request.getEmail(), "role", role.name()));

        // In production, send via email. For now, log the invite link.
        log.info("Invitation link: /api/v1/invitations/accept?token={}", rawToken);

        return rawToken;
    }

    /**
     * Accept an invitation: validate the token, create the user account.
     */
    @Transactional
    public User acceptInvite(AcceptInvitationRequest request) {
        String tokenHash = sha256(request.getToken());
        InvitationToken invite = invitationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("Invalid or expired invitation token"));

        if (invite.isAccepted()) {
            throw new BadRequestException("This invitation has already been accepted");
        }

        if (invite.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("Invitation token has expired");
        }

        if (userRepository.existsByEmail(invite.getEmail())) {
            throw new ConflictException("A user with this email already exists");
        }

        User user = User.builder()
                .email(invite.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(invite.getRole())
                .business(invite.getBusiness())
                .isEnabled(true)
                .provider("LOCAL")
                .build();
        user = userRepository.save(user);

        invite.setAccepted(true);
        invitationTokenRepository.save(invite);

        auditService.log(AuditEventType.INVITE_ACCEPTED, user.getId(), user.getId(),
                invite.getBusiness().getId(), null,
                Map.of("email", user.getEmail(), "role", user.getRole().name()));

        return user;
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
