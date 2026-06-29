package com.bookly.security;

import com.bookly.entity.AuditEventType;
import com.bookly.entity.RefreshToken;
import com.bookly.entity.User;
import com.bookly.repository.UserRepository;
import com.bookly.service.AuditService;
import com.bookly.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Handles successful Google OAuth2 login.
 * Links to an existing user (by email) or rejects if no account exists.
 * Business creation is a deliberate registration step, not an OAuth2 side-effect.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String googleId = oAuth2User.getAttribute("sub");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            redirectWithError(response, "Google account does not have an email");
            return;
        }

        Optional<User> existingUser = userRepository.findByEmail(email);

        if (existingUser.isEmpty()) {
            redirectWithError(response, "No account exists with this email. Please register first or accept an invitation.");
            return;
        }

        User user = existingUser.get();

        // Link Google provider if not already linked
        if ("LOCAL".equals(user.getProvider())) {
            user.setProvider("GOOGLE");
            user.setProviderId(googleId);
            userRepository.save(user);
        }

        CustomUserDetails userDetails = new CustomUserDetails(user);
        String accessToken = jwtUtils.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        auditService.log(AuditEventType.LOGIN_SUCCESS, user.getId(), user.getId(),
                user.getBusiness() != null ? user.getBusiness().getId() : null,
                request.getRemoteAddr(),
                Map.of("email", email, "provider", "GOOGLE"));

        // Redirect to frontend with tokens as query params
        String frontendUrl = allowedOrigins.split(",")[0];
        String redirectUrl = frontendUrl + "/oauth2/callback"
                + "?accessToken=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8)
                + "&refreshToken=" + URLEncoder.encode(refreshToken.getToken(), StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }

    private void redirectWithError(HttpServletResponse response, String error) throws IOException {
        String frontendUrl = allowedOrigins.split(",")[0];
        String redirectUrl = frontendUrl + "/oauth2/callback?error="
                + URLEncoder.encode(error, StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}
