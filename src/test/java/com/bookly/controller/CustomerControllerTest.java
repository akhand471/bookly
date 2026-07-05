package com.bookly.controller;

import com.bookly.config.SecurityConfig;
import com.bookly.config.TenantInterceptor;
import com.bookly.dto.*;
import com.bookly.entity.*;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.security.*;
import com.bookly.service.AppointmentService;
import com.bookly.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import jakarta.servlet.FilterChain;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CustomerService customerService;
    @MockBean private AppointmentService appointmentService;

    // Security infrastructure mocks required by @Import(SecurityConfig.class)
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private RateLimitingFilter rateLimitingFilter;
    @MockBean private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    @MockBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtUtils jwtUtils;
    @MockBean private TenantInterceptor tenantInterceptor;

    private UUID businessId;
    private UUID customerId;
    private CustomerResponse customerResponse;

    @BeforeEach
    void setUp() throws Exception {
        businessId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        customerResponse = CustomerResponse.builder()
                .id(customerId)
                .businessId(businessId)
                .firstName("Alex")
                .lastName("Smith")
                .email("alex@example.com")
                .phone("+1-555-0100")
                .build();

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

        doAnswer(inv -> {
            jakarta.servlet.http.HttpServletResponse response = inv.getArgument(1);
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(customAuthenticationEntryPoint).commence(any(), any(), any());

        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    /** Builds an authenticated OWNER token for MockMvc requests. */
    private UsernamePasswordAuthenticationToken ownerAuth() {
        Business business = Business.builder().id(businessId).name("Salon").subdomain("salon").build();
        User owner = User.builder()
                .id(UUID.randomUUID())
                .email("owner@salon.com")
                .firstName("Owner").lastName("User")
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .isEnabled(true)
                .build();
        CustomUserDetails details = new CustomUserDetails(owner);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void listCustomers_authenticated_returns200() throws Exception {
        PageResponse<CustomerResponse> page = PageResponse.<CustomerResponse>builder()
                .content(List.of(customerResponse))
                .page(0).size(20).totalElements(1).totalPages(1).build();

        when(customerService.listCustomers(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/customers")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].email").value("alex@example.com"));
    }

    @Test
    void listCustomers_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomer_found_returns200() throws Exception {
        when(customerService.getCustomerById(customerId)).thenReturn(customerResponse);

        mockMvc.perform(get("/api/v1/customers/" + customerId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("Alex"));
    }

    @Test
    void getCustomer_notFound_returns404() throws Exception {
        when(customerService.getCustomerById(customerId))
                .thenThrow(new ResourceNotFoundException("Customer not found: " + customerId));

        mockMvc.perform(get("/api/v1/customers/" + customerId)
                        .with(SecurityMockMvcRequestPostProcessors.authentication(ownerAuth())))
                .andExpect(status().isNotFound());
    }
}
