package com.bookly.security;

import com.bookly.entity.Business;
import com.bookly.entity.RefreshToken;
import com.bookly.entity.Role;
import com.bookly.entity.User;
import com.bookly.repository.UserRepository;
import com.bookly.service.AuditService;
import com.bookly.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OAuth2LoginSuccessHandlerTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtUtils jwtUtils;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AuditService auditService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private Authentication authentication;
    @Mock private OAuth2User oAuth2User;

    @InjectMocks
    private OAuth2LoginSuccessHandler handler;

    private User existingUser;
    private UUID businessId;

    @BeforeEach
    void setUp() {
        // Inject the @Value field that Spring normally sets
        ReflectionTestUtils.setField(handler, "allowedOrigins", "http://localhost:3000");

        businessId = UUID.randomUUID();
        Business business = new Business();
        business.setId(businessId);

        existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("alex@example.com")
                .passwordHash("")
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .isEnabled(true)
                .provider("LOCAL")
                .providerId(null)
                .build();

        when(authentication.getPrincipal()).thenReturn(oAuth2User);
        when(oAuth2User.getAttribute("email")).thenReturn("alex@example.com");
        when(oAuth2User.getAttribute("sub")).thenReturn("google-id-123");
        when(oAuth2User.getAttribute("name")).thenReturn("Alex");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void existingUser_loginSucceeds_redirectsWithTokens() throws Exception {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtUtils.generateToken(any(CustomUserDetails.class))).thenReturn("access.token.jwt");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token-value");
        refreshToken.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn(refreshToken);

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        String redirectUrl = redirectCaptor.getValue();
        assertThat(redirectUrl).contains("http://localhost:3000/oauth2/callback");
        assertThat(redirectUrl).contains("accessToken=");
        assertThat(redirectUrl).contains("refreshToken=");
    }

    @Test
    void existingLocalUser_googleProviderLinked() throws Exception {
        assertThat(existingUser.getProvider()).isEqualTo("LOCAL");
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtUtils.generateToken(any())).thenReturn("token");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("rt");
        refreshToken.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn(refreshToken);

        handler.onAuthenticationSuccess(request, response, authentication);

        // Provider should be updated to GOOGLE
        assertThat(existingUser.getProvider()).isEqualTo("GOOGLE");
        assertThat(existingUser.getProviderId()).isEqualTo("google-id-123");
        verify(userRepository).save(existingUser);
    }

    @Test
    void alreadyGoogleLinkedUser_saveNotCalledAgain() throws Exception {
        existingUser.setProvider("GOOGLE");
        existingUser.setProviderId("google-id-123");

        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(existingUser));
        when(jwtUtils.generateToken(any())).thenReturn("token");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("rt");
        refreshToken.setExpiryDate(Instant.now().plusSeconds(3600));
        when(refreshTokenService.createRefreshToken(existingUser)).thenReturn(refreshToken);

        handler.onAuthenticationSuccess(request, response, authentication);

        // Provider already GOOGLE — no redundant save
        verify(userRepository, never()).save(any());
    }

    @Test
    void noExistingAccount_redirectsWithError() throws Exception {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("error=");
        assertThat(redirectCaptor.getValue()).doesNotContain("accessToken=");
    }

    @Test
    void missingEmailFromGoogle_redirectsWithError() throws Exception {
        when(oAuth2User.getAttribute("email")).thenReturn(null);

        handler.onAuthenticationSuccess(request, response, authentication);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(redirectCaptor.capture());
        assertThat(redirectCaptor.getValue()).contains("error=");
        verifyNoInteractions(userRepository);
    }
}
