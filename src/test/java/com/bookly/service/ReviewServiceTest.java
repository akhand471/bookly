package com.bookly.service;

import com.bookly.dto.PageResponse;
import com.bookly.dto.PublicReviewResponse;
import com.bookly.dto.ReviewRequest;
import com.bookly.dto.ReviewResponse;
import com.bookly.entity.*;
import com.bookly.exception.BadRequestException;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.ReviewMapper;
import com.bookly.repository.AppointmentRepository;
import com.bookly.repository.ReviewRepository;
import com.bookly.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ReviewMapper reviewMapper;

    @InjectMocks private ReviewService reviewService;

    private UUID businessId;
    private UUID appointmentId;
    private UUID serviceId;
    private UUID staffId;
    private UUID customerId;

    private Business business;
    private Appointment appointment;
    private Customer customer;
    private BookableService service;
    private User staff;

    private ReviewRequest validRequest;
    private ReviewResponse sampleResponse;
    private PublicReviewResponse samplePublicResponse;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        customerId = UUID.randomUUID();

        business = Business.builder().id(businessId).name("Barber Shop").build();
        customer = Customer.builder().id(customerId).firstName("Alex").lastName("Smith").build();
        service = BookableService.builder().id(serviceId).name("Haircut").build();
        staff = User.builder().id(staffId).firstName("Jane").lastName("Doe").build();

        appointment = Appointment.builder()
                .id(appointmentId)
                .business(business)
                .customer(customer)
                .service(service)
                .staff(staff)
                .status(AppointmentStatus.COMPLETED)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                .build();

        validRequest = ReviewRequest.builder()
                .rating(5)
                .comment("Excellent work!")
                .build();

        sampleResponse = ReviewResponse.builder()
                .id(UUID.randomUUID())
                .appointmentId(appointmentId)
                .serviceId(serviceId)
                .serviceName("Haircut")
                .staffId(staffId)
                .staffName("Jane Doe")
                .customerName("Alex Smith")
                .rating(5)
                .comment("Excellent work!")
                .build();

        samplePublicResponse = PublicReviewResponse.builder()
                .customerName("Alex S.")
                .rating(5)
                .comment("Excellent work!")
                .build();
    }

    @Test
    void createReview_ShouldPersistAndReturn_WhenValid() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(reviewRepository.findByAppointment_Id(appointmentId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any(Review.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewMapper.toResponse(any())).thenReturn(sampleResponse);

        ReviewResponse response = reviewService.createReview(appointmentId, validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getRating()).isEqualTo(5);
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void createReview_ShouldThrowBadRequest_WhenAppointmentNotCompleted() {
        appointment.setStatus(AppointmentStatus.PENDING);
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> reviewService.createReview(appointmentId, validRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Only completed appointments");
    }

    @Test
    void createReview_ShouldThrowConflict_WhenAlreadyReviewed() {
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(reviewRepository.findByAppointment_Id(appointmentId)).thenReturn(Optional.of(new Review()));

        assertThatThrownBy(() -> reviewService.createReview(appointmentId, validRequest))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already been reviewed");
    }

    @Test
    void createReview_ShouldThrowBadRequest_WhenPastReviewWindow() {
        // Completed 15 days ago
        appointment.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).minusDays(15));
        when(appointmentRepository.findById(appointmentId)).thenReturn(Optional.of(appointment));
        when(reviewRepository.findByAppointment_Id(appointmentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.createReview(appointmentId, validRequest))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("review window for this appointment has expired");
    }

    @Test
    void getReviewsForService_ShouldReturnPage() {
        TenantContext.setCurrentTenant(businessId);
        Pageable pageable = PageRequest.of(0, 10);
        Review review = Review.builder().customer(customer).build();
        Page<Review> page = new PageImpl<>(List.of(review), pageable, 1);

        when(reviewRepository.findAllByService_IdAndBusiness_Id(serviceId, businessId, pageable)).thenReturn(page);
        when(reviewMapper.toPublicResponse(review)).thenReturn(samplePublicResponse);

        PageResponse<PublicReviewResponse> response = reviewService.getReviewsForService(serviceId, pageable);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getCustomerName()).isEqualTo("Alex S.");
        TenantContext.clear();
    }
}
