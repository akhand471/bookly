package com.bookly.controller;

import com.bookly.dto.*;
import com.bookly.entity.AuditEventType;
import com.bookly.security.CustomUserDetails;
import com.bookly.service.AuditService;
import com.bookly.service.AuthService;
import com.bookly.service.RefreshTokenService;
import com.bookly.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and registration operations")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    // UserService encapsulates user lookups — controllers must NOT inject repositories directly
    private final UserService userService;

    @PostMapping("/register")
    @Operation(summary = "Register a new business and owner account")
    public ResponseEntity<ApiResponse<TokenResponse>> register(
            @Valid @RequestBody RegisterBusinessRequest request) {
        TokenResponse response = authService.registerBusiness(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Business and owner registered successfully"));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user credentials and return JWT details")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renew current access token using valid refresh token")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        TokenResponse response = authService.refreshAccessToken(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed successfully"));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Invalidate refresh token session for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal CustomUserDetails userDetails) {
        // Delegate user lookup to UserService — not repository
        refreshTokenService.deleteByUser(userService.findById(userDetails.getId()));
        auditService.log(AuditEventType.LOGOUT, userDetails.getId(), userDetails.getId(),
                userDetails.getBusinessId(), null, Map.of("email", userDetails.getEmail()));
        return ResponseEntity.ok(ApiResponse.success(null, "Logged out successfully"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get user details of the current active session")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        // UserService handles the entity fetch and DTO mapping
        UserResponse response = userService.getUserById(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Profile details retrieved successfully"));
    }
}
