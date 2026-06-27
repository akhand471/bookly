package com.bookly.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bookly.config.SecurityConfig;
import com.bookly.dto.LoginRequest;
import com.bookly.dto.RefreshTokenRequest;
import com.bookly.dto.RegisterBusinessRequest;
import com.bookly.dto.TokenResponse;
import com.bookly.dto.UserResponse;
import com.bookly.entity.Business;
import com.bookly.entity.Role;
import com.bookly.entity.User;
import com.bookly.security.CustomAuthenticationEntryPoint;
import com.bookly.security.CustomUserDetails;
import com.bookly.security.CustomUserDetailsService;
import com.bookly.security.JwtAuthenticationFilter;
import com.bookly.security.JwtUtils;
import com.bookly.service.AuthService;
import com.bookly.service.RefreshTokenService;
import com.bookly.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private RegisterBusinessRequest validRegisterRequest;
    private LoginRequest validLoginRequest;

    @BeforeEach
    void setUp() throws Exception {
        validRegisterRequest = RegisterBusinessRequest.builder()
                .businessName("Barber Shop")
                .subdomain("barber")
                .ownerFirstName("Alex")
                .ownerLastName("Groom")
                .email("alex@barber.com")
                .password("password123")
                .build();

        validLoginRequest = LoginRequest.builder()
                .email("alex@barber.com")
                .password("password123")
                .build();

        // Stub the mock JWT filter to act as a pass-through in the Security Filter Chain
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void register_ShouldReturnOk_WhenPayloadIsValid() throws Exception {
        TokenResponse response = TokenResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .role("BUSINESS_OWNER")
                .email("alex@barber.com")
                .build();

        when(authService.registerBusiness(any(RegisterBusinessRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.message").value("Business and owner registered successfully"));
    }

    @Test
    void register_ShouldReturnBadRequest_WhenEmailIsInvalid() throws Exception {
        // Create an invalid request payload
        RegisterBusinessRequest invalidRequest = RegisterBusinessRequest.builder()
                .businessName("Barber Shop")
                .subdomain("barber")
                .ownerFirstName("Alex")
                .ownerLastName("Groom")
                .email("invalid-email")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.data.email").exists());
    }

    @Test
    void login_ShouldReturnOk_WhenPayloadIsValid() throws Exception {
        TokenResponse response = TokenResponse.builder()
                .accessToken("access")
                .refreshToken("refresh")
                .role("BUSINESS_OWNER")
                .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validLoginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"));
    }

    @Test
    void refresh_ShouldReturnOk_WhenPayloadIsValid() throws Exception {
        RefreshTokenRequest request = new RefreshTokenRequest("refresh");
        TokenResponse response = TokenResponse.builder()
                .accessToken("new_access")
                .refreshToken("new_refresh")
                .build();

        when(authService.refreshAccessToken(any(RefreshTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("new_access"));
    }

    @Test
    void getMe_ShouldReturnUserProfile_WhenAuthenticated() throws Exception {
        Business business = Business.builder().id(UUID.randomUUID()).name("Barber Shop").build();
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("alex@barber.com")
                .firstName("Alex")
                .lastName("Groom")
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .email("alex@barber.com")
                .firstName("Alex")
                .lastName("Groom")
                .role("BUSINESS_OWNER")
                .businessId(business.getId())
                .build();
        when(userService.getUserById(any(UUID.class))).thenReturn(userResponse);

        mockMvc.perform(get("/api/v1/auth/me")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("alex@barber.com"))
                .andExpect(jsonPath("$.data.role").value("BUSINESS_OWNER"));
    }
}
