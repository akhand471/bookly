package com.scheduly.controller;

import com.scheduly.dto.*;
import com.scheduly.security.CustomUserDetails;
import com.scheduly.service.AuthService;
import com.scheduly.service.RefreshTokenService;
import com.scheduly.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and registration operations")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;
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
