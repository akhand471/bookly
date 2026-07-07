package com.bookly.controller;

import com.bookly.config.SecurityConfig;
import com.bookly.config.TenantInterceptor;
import com.bookly.dto.*;
import com.bookly.entity.Business;
import com.bookly.entity.Role;
import com.bookly.entity.User;
import com.bookly.security.*;
import com.bookly.service.BusinessService;
import com.bookly.service.PublicBookingService;
import com.bookly.service.ReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ReviewController.class, PublicBookingController.class})
@Import(SecurityConfig.class)
class ReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private ReviewService reviewService;
    @MockBean private PublicBookingService publicBookingService;
    @MockBean private BusinessService businessService;

    // Security infrastructure mocks
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private RateLimitingFilter rateLimitingFilter;
    @MockBean private CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    @MockBean private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private JwtUtils jwtUtils;
    @MockBean private TenantInterceptor tenantInterceptor;

    private UUID businessId;
    private UUID appointmentId;
    private ReviewResponse reviewResponse;
    private PublicReviewResponse publicReviewResponse;
    private Business business;

    @BeforeEach
    void setUp() throws Exception {
        businessId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();

        business = Business.builder().id(businessId).name("Salon").subdomain("salon").build();

        reviewResponse = ReviewResponse.builder()
                .id(UUID.randomUUID())
                .appointmentId(appointmentId)
                .customerName("Alex Smith")
                .rating(5)
                .comment("Excellent!")
                .build();

        publicReviewResponse = PublicReviewResponse.builder()
                .customerName("Alex S.")
                .rating(5)
                .comment("Excellent!")
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

        when(tenantInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    private UsernamePasswordAuthenticationToken staffAuth() {
        User staff = User.builder()
                .id(UUID.randomUUID())
                .email("staff@salon.com")
                .role(Role.EMPLOYEE)
                .business(business)
                .isEnabled(true)
                .build();
        CustomUserDetails details = new CustomUserDetails(staff);
        return new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
    }

    @Test
    void submitReview_public_returns201() throws Exception {
        ReviewRequest request = ReviewRequest.builder().rating(5).comment("Excellent!").build();
        when(reviewService.createReview(eq(appointmentId), any(ReviewRequest.class)))
                .thenReturn(reviewResponse);

        mockMvc.perform(post("/api/v1/public/salon/bookings/" + appointmentId + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.comment").value("Excellent!"));
    }

    @Test
    void getServiceReviews_public_returns200() throws Exception {
        UUID serviceId = UUID.randomUUID();
        PageResponse<PublicReviewResponse> page = PageResponse.<PublicReviewResponse>builder()
                .content(List.of(publicReviewResponse))
                .page(0).size(10).totalElements(1).totalPages(1).build();

        when(businessService.getBusinessBySubdomain("salon")).thenReturn(business);
        when(reviewService.getReviewsForService(eq(serviceId), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/public/salon/services/" + serviceId + "/reviews")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerName").value("Alex S."));
    }

    @Test
    void getStaffReviews_public_returns200() throws Exception {
        UUID staffId = UUID.randomUUID();
        PageResponse<PublicReviewResponse> page = PageResponse.<PublicReviewResponse>builder()
                .content(List.of(publicReviewResponse))
                .page(0).size(10).totalElements(1).totalPages(1).build();

        when(businessService.getBusinessBySubdomain("salon")).thenReturn(business);
        when(reviewService.getReviewsForStaff(eq(staffId), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/public/salon/staff/" + staffId + "/reviews")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerName").value("Alex S."));
    }

    @Test
    void listReviews_authenticated_returns200() throws Exception {
        PageResponse<ReviewResponse> page = PageResponse.<ReviewResponse>builder()
                .content(List.of(reviewResponse))
                .page(0).size(20).totalElements(1).totalPages(1).build();

        when(reviewService.getBusinessReviews(any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/reviews")
                        .with(SecurityMockMvcRequestPostProcessors.authentication(staffAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customerName").value("Alex Smith"));
    }

    @Test
    void listReviews_unauthenticated_returns401() throws Exception {
        doAnswer(inv -> {
            jakarta.servlet.http.HttpServletResponse response = inv.getArgument(1);
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            return null;
        }).when(customAuthenticationEntryPoint).commence(any(), any(), any());

        mockMvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isUnauthorized());
    }
}
