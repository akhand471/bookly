package com.bookly.controller;

import com.bookly.dto.*;
import com.bookly.entity.User;
import com.bookly.security.CustomUserDetails;
import com.bookly.service.InvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
@Tag(name = "Invitations", description = "Employee invitation management")
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(summary = "Invite an employee to join your business")
    public ResponseEntity<ApiResponse<Void>> createInvitation(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateInvitationRequest request) {
        invitationService.createInvite(userDetails.getBusinessId(), userDetails.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(null, "Invitation sent successfully"));
    }

    @PostMapping("/accept")
    @Operation(summary = "Accept an invitation and create your account")
    public ResponseEntity<ApiResponse<Void>> acceptInvitation(
            @Valid @RequestBody AcceptInvitationRequest request) {
        User user = invitationService.acceptInvite(request);
        return ResponseEntity.ok(ApiResponse.success(null,
                "Account created successfully for " + user.getEmail()));
    }
}
