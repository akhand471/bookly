package com.scheduly.service;

import com.scheduly.dto.LoginRequest;
import com.scheduly.dto.RefreshTokenRequest;
import com.scheduly.dto.RegisterBusinessRequest;
import com.scheduly.dto.TokenResponse;
import com.scheduly.entity.Business;
import com.scheduly.entity.Role;
import com.scheduly.entity.User;
import com.scheduly.entity.RefreshToken;
import com.scheduly.exception.ConflictException;
import com.scheduly.exception.UnauthorizedException;
import com.scheduly.repository.BusinessRepository;
import com.scheduly.repository.UserRepository;
import com.scheduly.security.CustomUserDetails;
import com.scheduly.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BusinessRepository businessRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private RegisterBusinessRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterBusinessRequest.builder()
                .businessName("Test Business")
                .subdomain("test")
                .ownerFirstName("John")
                .ownerLastName("Doe")
                .email("john.doe@test.com")
                .password("password123")
                .build();

        loginRequest = LoginRequest.builder()
                .email("john.doe@test.com")
                .password("password123")
                .build();
    }

    @Test
    void registerBusiness_ShouldRegisterSuccessfully_WhenRequestIsValid() {
        // Arrange
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(businessRepository.existsBySubdomain(any())).thenReturn(false);
        
        Business savedBusiness = Business.builder()
                .id(UUID.randomUUID())
                .name(registerRequest.getBusinessName())
                .subdomain(registerRequest.getSubdomain())
                .build();
        when(businessRepository.save(any(Business.class))).thenReturn(savedBusiness);

        User savedUser = User.builder()
                .id(UUID.randomUUID())
                .email(registerRequest.getEmail())
                .firstName(registerRequest.getOwnerFirstName())
                .lastName(registerRequest.getOwnerLastName())
                .role(Role.BUSINESS_OWNER)
                .business(savedBusiness)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(passwordEncoder.encode(any())).thenReturn("hashed_password");
        when(jwtUtils.generateToken(any())).thenReturn("access_token");

        RefreshToken mockRefreshToken = RefreshToken.builder()
                .token("refresh_token")
                .build();
        when(refreshTokenService.createRefreshToken(any())).thenReturn(mockRefreshToken);

        // Act
        TokenResponse response = authService.registerBusiness(registerRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertEquals("BUSINESS_OWNER", response.getRole());
        assertEquals(registerRequest.getEmail(), response.getEmail());
        assertEquals(savedBusiness.getId(), response.getBusinessId());

        verify(businessRepository, times(1)).save(any(Business.class));
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void registerBusiness_ShouldThrowConflictException_WhenEmailIsAlreadyInUse() {
        // Arrange
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        // Act & Assert
        assertThrows(ConflictException.class, () -> authService.registerBusiness(registerRequest));
        verify(businessRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerBusiness_ShouldThrowConflictException_WhenSubdomainIsAlreadyInUse() {
        // Arrange
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(businessRepository.existsBySubdomain(registerRequest.getSubdomain())).thenReturn(true);

        // Act & Assert
        assertThrows(ConflictException.class, () -> authService.registerBusiness(registerRequest));
        verify(businessRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_ShouldAuthenticateAndReturnTokens_WhenCredentialsAreValid() {
        // Arrange
        Authentication authentication = mock(Authentication.class);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(loginRequest.getEmail())
                .role(Role.BUSINESS_OWNER)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail(loginRequest.getEmail())).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(any())).thenReturn("access_token");

        RefreshToken mockRefreshToken = RefreshToken.builder()
                .token("refresh_token")
                .build();
        when(refreshTokenService.createRefreshToken(any())).thenReturn(mockRefreshToken);

        // Act
        TokenResponse response = authService.login(loginRequest);

        // Assert
        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertEquals("BUSINESS_OWNER", response.getRole());
        assertEquals(loginRequest.getEmail(), response.getEmail());
    }

    @Test
    void refreshAccessToken_ShouldRotateTokens_WhenRefreshTokenIsValid() {
        // Arrange
        String tokenStr = "valid_refresh_token";
        RefreshTokenRequest request = new RefreshTokenRequest(tokenStr);

        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@test.com")
                .role(Role.CUSTOMER)
                .build();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(tokenStr)
                .user(user)
                .expiryDate(Instant.now().plusSeconds(3600))
                .build();

        when(refreshTokenService.findByToken(tokenStr)).thenReturn(Optional.of(refreshToken));
        when(refreshTokenService.verifyExpiration(refreshToken)).thenReturn(refreshToken);
        when(jwtUtils.generateToken(any())).thenReturn("new_access_token");

        RefreshToken newRefreshToken = RefreshToken.builder()
                .token("new_refresh_token")
                .build();
        when(refreshTokenService.createRefreshToken(user)).thenReturn(newRefreshToken);

        // Act
        TokenResponse response = authService.refreshAccessToken(request);

        // Assert
        assertNotNull(response);
        assertEquals("new_access_token", response.getAccessToken());
        assertEquals("new_refresh_token", response.getRefreshToken());
    }
}
