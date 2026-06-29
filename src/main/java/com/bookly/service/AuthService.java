package com.bookly.service;

import com.bookly.dto.LoginRequest;
import com.bookly.dto.RegisterBusinessRequest;
import com.bookly.dto.RefreshTokenRequest;
import com.bookly.dto.TokenResponse;
import com.bookly.entity.*;
import com.bookly.exception.ConflictException;
import com.bookly.exception.UnauthorizedException;
import com.bookly.repository.BusinessRepository;
import com.bookly.repository.UserRepository;
import com.bookly.security.CustomUserDetails;
import com.bookly.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public TokenResponse registerBusiness(RegisterBusinessRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email address is already in use");
        }

        if (businessRepository.existsBySubdomain(request.getSubdomain())) {
            throw new ConflictException("Subdomain is already taken");
        }

        // 1. Create Business
        Business business = Business.builder()
                .name(request.getBusinessName())
                .subdomain(request.getSubdomain())
                .build();
        business = businessRepository.save(business);

        // 2. Create Owner User
        User owner = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getOwnerFirstName())
                .lastName(request.getOwnerLastName())
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .isEnabled(true)
                .provider("LOCAL")
                .build();
        owner = userRepository.save(owner);

        // 3. Generate Auth Tokens
        CustomUserDetails userDetails = new CustomUserDetails(owner);
        String accessToken = jwtUtils.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(owner, getDeviceId(), getUserAgent());

        // 4. Audit
        auditService.log(AuditEventType.REGISTRATION, owner.getId(), owner.getId(),
                business.getId(), getClientIp(),
                Map.of("email", owner.getEmail(), "subdomain", business.getSubdomain()));

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .role(owner.getRole().name())
                .email(owner.getEmail())
                .businessId(business.getId())
                .build();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userRepository.findByEmail(userDetails.getEmail())
                    .orElseThrow(() -> new UnauthorizedException("User details not found"));

            String accessToken = jwtUtils.generateToken(userDetails);
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user, getDeviceId(), getUserAgent());

            // Audit successful login
            auditService.log(AuditEventType.LOGIN_SUCCESS, user.getId(), user.getId(),
                    userDetails.getBusinessId(), getClientIp(),
                    Map.of("email", user.getEmail()));

            return TokenResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken.getToken())
                    .role(user.getRole().name())
                    .email(user.getEmail())
                    .businessId(userDetails.getBusinessId())
                    .build();
        } catch (BadCredentialsException e) {
            // Audit failed login — actor unknown, use email in details
            auditService.log(AuditEventType.LOGIN_FAILURE, null, null,
                    null, getClientIp(),
                    Map.of("email", request.getEmail()));
            throw e;
        }
    }

    @Transactional
    public TokenResponse refreshAccessToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    CustomUserDetails userDetails = new CustomUserDetails(user);
                    String newAccessToken = jwtUtils.generateToken(userDetails);
                    // Standard JWT rotation: regenerate the refresh token to prevent reuse attacks
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user, getDeviceId(), getUserAgent());
                    
                    return TokenResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(newRefreshToken.getToken())
                            .role(user.getRole().name())
                            .email(user.getEmail())
                            .businessId(user.getBusiness() != null ? user.getBusiness().getId() : null)
                            .build();
                })
                .orElseThrow(() -> new UnauthorizedException("Refresh token is not in the database"));
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String getDeviceId() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("X-Device-Id");
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String getUserAgent() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                return attrs.getRequest().getHeader("User-Agent");
            }
        } catch (Exception ignored) {}
        return null;
    }
}
