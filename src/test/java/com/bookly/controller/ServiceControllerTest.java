package com.bookly.controller;

import com.bookly.config.SecurityConfig;
import com.bookly.config.TenantInterceptor;
import com.bookly.dto.PageResponse;
import com.bookly.dto.ServiceRequest;
import com.bookly.dto.ServiceResponse;
import com.bookly.entity.Business;
import com.bookly.entity.Role;
import com.bookly.entity.User;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.security.CustomAuthenticationEntryPoint;
import com.bookly.security.CustomUserDetails;
import com.bookly.security.CustomUserDetailsService;
import com.bookly.security.JwtAuthenticationFilter;
import com.bookly.security.JwtUtils;
import com.bookly.security.OAuth2LoginSuccessHandler;
import com.bookly.security.RateLimitingFilter;
import com.bookly.service.BookableServiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ServiceController.class)
@Import(SecurityConfig.class)
class ServiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookableServiceService bookableServiceService;

    // ── Security infrastructure mocks (required by SecurityConfig import) ──
    @MockBean private JwtUtils jwtUtils;
    @MockBean private CustomUserDetailsService userDetailsService;
    @MockBean private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private RateLimitingFilter rateLimitingFilter;
    @MockBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean private TenantInterceptor tenantInterceptor;

    private UUID businessId;
    private UUID serviceId;
    private ServiceRequest validRequest;
    private ServiceResponse sampleResponse;

    // Helper: build an authenticated BUSINESS_OWNER principal for MockMvc
    private UsernamePasswordAuthenticationToken ownerAuth() {
        Business business = Business.builder().id(businessId).name("Test Salon").build();
        User owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@salon.com")
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .build();
        CustomUserDetails details = new CustomUserDetails(owner);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    // Helper: build an authenticated EMPLOYEE principal
    private UsernamePasswordAuthenticationToken employeeAuth() {
        Business business = Business.builder().id(businessId).name("Test Salon").build();
        User employee = User.builder()
                .id(UUID.randomUUID())
                .email("staff@salon.com")
                .role(Role.EMPLOYEE)
                .business(business)
                .build();
        CustomUserDetails details = new CustomUserDetails(employee);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @BeforeEach
    void setUp() throws Exception {
        businessId = UUID.randomUUID();
        serviceId = UUID.randomUUID();

        validRequest = ServiceRequest.builder()
                .name("Haircut")
                .description("Classic cut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .isActive(true)
                .build();

        sampleResponse = ServiceResponse.builder()
                .id(serviceId)
                .businessId(businessId)
                .name("Haircut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .isActive(true)
                .build();

        // Pass-through stubs for filter chain
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());

        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    // ─── POST /api/v1/services ─────────────────────────────────────────────

    @Test
    void create_ShouldReturn201_WhenOwnerSubmitsValidRequest() throws Exception {
        when(bookableServiceService.create(any(ServiceRequest.class))).thenReturn(sampleResponse);

        mockMvc.perform(post("/api/v1/services")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Haircut"))
                .andExpect(jsonPath("$.data.durationMinutes").value(30))
                .andExpect(jsonPath("$.message").value("Service created successfully"));
    }

    @Test
    void create_ShouldReturn403_WhenEmployeeTriesToCreate() throws Exception {
        mockMvc.perform(post("/api/v1/services")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(employeeAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden());

        verify(bookableServiceService, never()).create(any());
    }

    @Test
    void create_ShouldReturn400_WhenRequestBodyIsInvalid() throws Exception {
        ServiceRequest bad = ServiceRequest.builder()
                .name("") // blank name — violates @NotBlank
                .durationMinutes(-1) // violates @Min(1)
                .price(new BigDecimal("-5")) // violates @DecimalMin
                .build();

        mockMvc.perform(post("/api/v1/services")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    @Test
    void create_ShouldReturn409_WhenServiceNameAlreadyExists() throws Exception {
        when(bookableServiceService.create(any()))
                .thenThrow(new ConflictException("A service named 'Haircut' already exists in this business"));

        mockMvc.perform(post("/api/v1/services")
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /api/v1/services ──────────────────────────────────────────────

    @Test
    void list_ShouldReturn200WithPage_WhenAuthenticated() throws Exception {
        PageResponse<ServiceResponse> page = PageResponse.<ServiceResponse>builder()
                .content(List.of(sampleResponse))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();

        when(bookableServiceService.listActive(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/services")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(employeeAuth()))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].name").value("Haircut"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void list_ShouldReturn200_WhenEmployeeAuthenticated() throws Exception {
        // This also implicitly validates that EMPLOYEE role can access read-only endpoints.
        // The corresponding 401-for-unauthenticated behaviour is enforced at the security
        // config level (.anyRequest().authenticated()) and covered by the full context test.
        PageResponse<ServiceResponse> page = PageResponse.<ServiceResponse>builder()
                .content(List.of(sampleResponse))
                .page(0).size(20).totalElements(1).totalPages(1).last(true)
                .build();
        when(bookableServiceService.listActive(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/services")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(employeeAuth()))
                        .param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ─── GET /api/v1/services/{id} ─────────────────────────────────────────

    @Test
    void getById_ShouldReturn200_WhenServiceExists() throws Exception {
        when(bookableServiceService.getById(serviceId)).thenReturn(sampleResponse);

        mockMvc.perform(get("/api/v1/services/{id}", serviceId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(employeeAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(serviceId.toString()))
                .andExpect(jsonPath("$.data.businessId").value(businessId.toString()));
    }

    @Test
    void getById_ShouldReturn404_WhenServiceNotInTenant() throws Exception {
        when(bookableServiceService.getById(any()))
                .thenThrow(new ResourceNotFoundException("Service not found: " + serviceId));

        mockMvc.perform(get("/api/v1/services/{id}", serviceId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(employeeAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ─── PUT /api/v1/services/{id} ─────────────────────────────────────────

    @Test
    void update_ShouldReturn200_WhenOwnerUpdates() throws Exception {
        ServiceResponse updated = ServiceResponse.builder()
                .id(serviceId).name("Haircut Deluxe").durationMinutes(45)
                .price(new BigDecimal("35.00")).isActive(true).build();
        when(bookableServiceService.update(eq(serviceId), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/services/{id}", serviceId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Haircut Deluxe"))
                .andExpect(jsonPath("$.message").value("Service updated successfully"));
    }

    @Test
    void update_ShouldReturn403_WhenEmployeeTries() throws Exception {
        mockMvc.perform(put("/api/v1/services/{id}", serviceId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(employeeAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isForbidden());
    }

    // ─── DELETE /api/v1/services/{id} ──────────────────────────────────────

    @Test
    void delete_ShouldReturn200_WhenOwnerDeletes() throws Exception {
        doNothing().when(bookableServiceService).softDelete(serviceId);

        mockMvc.perform(delete("/api/v1/services/{id}", serviceId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Service deleted successfully"));
    }

    @Test
    void delete_ShouldReturn403_WhenEmployeeTries() throws Exception {
        mockMvc.perform(delete("/api/v1/services/{id}", serviceId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(employeeAuth())))
                .andExpect(status().isForbidden());

        verify(bookableServiceService, never()).softDelete(any());
    }

    @Test
    void delete_ShouldReturn404_WhenServiceNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Service not found: " + serviceId))
                .when(bookableServiceService).softDelete(serviceId);

        mockMvc.perform(delete("/api/v1/services/{id}", serviceId)
                        .with(csrf())
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}
