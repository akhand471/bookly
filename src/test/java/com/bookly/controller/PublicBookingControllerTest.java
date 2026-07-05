package com.bookly.controller;

import com.bookly.config.SecurityConfig;
import com.bookly.config.TenantInterceptor;
import com.bookly.dto.*;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.security.*;
import com.bookly.service.PublicBookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.FilterChain;
import static org.mockito.Mockito.doAnswer;

@WebMvcTest(PublicBookingController.class)
@Import(SecurityConfig.class)
class PublicBookingControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PublicBookingService publicBookingService;

    // Security infrastructure mocks required by @Import(SecurityConfig.class)
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private RateLimitingFilter rateLimitingFilter;
    @MockBean private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    @MockBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtUtils jwtUtils;
    @MockBean private TenantInterceptor tenantInterceptor;

    private PublicServiceResponse serviceResponse;
    private PublicBookingResponse bookingResponse;

    @BeforeEach
    void setUp() throws Exception {
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

        serviceResponse = PublicServiceResponse.builder()
                .id(UUID.randomUUID())
                .name("Classic Haircut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .build();

        bookingResponse = PublicBookingResponse.builder()
                .bookingId(UUID.randomUUID())
                .businessName("Barber Shop")
                .serviceName("Classic Haircut")
                .durationMinutes(30)
                .staffName("Jane Doe")
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).plusMinutes(30))
                .status("PENDING")
                .customerEmail("alex@example.com")
                .build();
    }

    @Test
    void getServices_validSubdomain_returns200() throws Exception {
        when(publicBookingService.getPublicServices("barber")).thenReturn(List.of(serviceResponse));

        mockMvc.perform(get("/api/v1/public/barber/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Classic Haircut"));
    }

    @Test
    void getServices_unknownSubdomain_returns404() throws Exception {
        when(publicBookingService.getPublicServices("unknown"))
                .thenThrow(new ResourceNotFoundException("No active business found for subdomain: unknown"));

        mockMvc.perform(get("/api/v1/public/unknown/services"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createBooking_validRequest_returns201() throws Exception {
        GuestBookingRequest request = GuestBookingRequest.builder()
                .serviceId(UUID.randomUUID())
                .staffId(UUID.randomUUID())
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3))
                .customerFirstName("Alex")
                .customerLastName("Smith")
                .customerEmail("alex@example.com")
                .customerPhone("+1-555-0100")
                .build();

        when(publicBookingService.createGuestBooking(eq("barber"), any())).thenReturn(bookingResponse);

        mockMvc.perform(post("/api/v1/public/barber/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerEmail").value("alex@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void createBooking_missingRequiredFields_returns400() throws Exception {
        // Missing customerFirstName, customerLastName, customerEmail, startTime
        GuestBookingRequest incomplete = GuestBookingRequest.builder()
                .serviceId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/v1/public/barber/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(incomplete)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBooking_slotConflict_returns409() throws Exception {
        GuestBookingRequest request = GuestBookingRequest.builder()
                .serviceId(UUID.randomUUID())
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(3))
                .customerFirstName("Alex")
                .customerLastName("Smith")
                .customerEmail("alex@example.com")
                .build();

        when(publicBookingService.createGuestBooking(eq("barber"), any()))
                .thenThrow(new ConflictException("The requested time slot is not available."));

        mockMvc.perform(post("/api/v1/public/barber/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
