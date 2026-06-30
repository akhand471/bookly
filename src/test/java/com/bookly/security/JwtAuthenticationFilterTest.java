package com.bookly.security;

import com.bookly.entity.Business;
import com.bookly.entity.Role;
import com.bookly.entity.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtAuthenticationFilter.
 * Verifies that TenantContext is populated from the live DB-loaded user record,
 * not from the (potentially stale) JWT claim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    @Mock private JwtUtils jwtUtils;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private static final String VALID_TOKEN = "valid.jwt.token";
    private static final UUID ORIGINAL_BUSINESS_ID = UUID.randomUUID();
    private static final UUID NEW_BUSINESS_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
        when(request.getHeader("Authorization")).thenReturn("Bearer " + VALID_TOKEN);
        when(jwtUtils.validateToken(VALID_TOKEN)).thenReturn(true);
        when(jwtUtils.getUsernameFromToken(VALID_TOKEN)).thenReturn("user@example.com");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    private CustomUserDetails buildUserDetails(UUID businessId) {
        Business business = new Business();
        business.setId(businessId);
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .isEnabled(true)
                .provider("LOCAL")
                .build();
        return new CustomUserDetails(user);
    }

    @Test
    void tenantContext_setFromDbRecord_notJwtClaim() throws Exception {
        // The DB now has NEW_BUSINESS_ID — capture TenantContext during the filter chain
        // (before the finally block clears it) to verify it matches the DB value.
        CustomUserDetails freshDbUser = buildUserDetails(NEW_BUSINESS_ID);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(freshDbUser);

        UUID[] capturedTenant = new UUID[1];
        doAnswer(inv -> {
            capturedTenant[0] = TenantContext.getCurrentTenant();
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilterInternal(request, response, filterChain);

        // TenantContext during request must have been set to the DB (live) businessId
        assertThat(capturedTenant[0]).isEqualTo(NEW_BUSINESS_ID);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void tenantContext_clearedAfterRequest() throws Exception {
        CustomUserDetails userDetails = buildUserDetails(ORIGINAL_BUSINESS_ID);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);

        filter.doFilterInternal(request, response, filterChain);

        // TenantContext must be cleared in the finally block
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void tenantContext_clearedEvenWhenFilterChainThrows() throws Exception {
        CustomUserDetails userDetails = buildUserDetails(ORIGINAL_BUSINESS_ID);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        doThrow(new RuntimeException("downstream error")).when(filterChain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (RuntimeException ignored) {}

        // TenantContext must be cleared regardless
        assertThat(TenantContext.getCurrentTenant()).isNull();
    }

    @Test
    void noAuthHeader_tenantContextNotSet() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(TenantContext.getCurrentTenant()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidToken_tenantContextNotSet() throws Exception {
        when(jwtUtils.validateToken(VALID_TOKEN)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(TenantContext.getCurrentTenant()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void userWithNoBusinessId_tenantContextRemainsNull() throws Exception {
        // SUPER_ADMIN users may have no business association
        User superAdmin = User.builder()
                .id(UUID.randomUUID())
                .email("user@example.com")
                .passwordHash("hash")
                .role(Role.SUPER_ADMIN)
                .business(null)
                .isEnabled(true)
                .provider("LOCAL")
                .build();
        CustomUserDetails adminDetails = new CustomUserDetails(superAdmin);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(adminDetails);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(TenantContext.getCurrentTenant()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
