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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;
    private final ReviewMapper reviewMapper;

    /**
     * Creates a customer review for a completed appointment.
     * Enforces that:
     * 1. The appointment exists.
     * 2. The status is COMPLETED.
     * 3. The review is created within 14 days of appointment completion.
     * 4. The appointment has not already been reviewed.
     */
    @Transactional
    public ReviewResponse createReview(UUID appointmentId, ReviewRequest request) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment not found: " + appointmentId));

        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Only completed appointments can be reviewed.");
        }

        if (reviewRepository.findByAppointment_Id(appointmentId).isPresent()) {
            throw new ConflictException("This appointment has already been reviewed.");
        }

        // Limit reviews to 14 days after appointment completion
        if (appointment.getEndTime().plusDays(14).isBefore(OffsetDateTime.now())) {
            throw new BadRequestException("The review window for this appointment has expired.");
        }

        Review review = Review.builder()
                .business(appointment.getBusiness())
                .appointment(appointment)
                .customer(appointment.getCustomer())
                .service(appointment.getService())
                .staff(appointment.getStaff())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        Review saved = reviewRepository.save(review);
        log.info("Review created: id={}, appointmentId={}, rating={}", saved.getId(), appointmentId, saved.getRating());
        return reviewMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicReviewResponse> getReviewsForService(UUID serviceId, Pageable pageable) {
        UUID businessId = TenantContext.getCurrentTenant();
        if (businessId == null) {
            throw new BadRequestException("Tenant context must be set.");
        }
        return PageResponse.from(
                reviewRepository.findAllByService_IdAndBusiness_Id(serviceId, businessId, pageable)
                        .map(reviewMapper::toPublicResponse)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicReviewResponse> getReviewsForStaff(UUID staffId, Pageable pageable) {
        UUID businessId = TenantContext.getCurrentTenant();
        if (businessId == null) {
            throw new BadRequestException("Tenant context must be set.");
        }
        return PageResponse.from(
                reviewRepository.findAllByStaff_IdAndBusiness_Id(staffId, businessId, pageable)
                        .map(reviewMapper::toPublicResponse)
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getBusinessReviews(Pageable pageable) {
        UUID businessId = TenantContext.getCurrentTenant();
        if (businessId == null) {
            throw new BadRequestException("Tenant context must be set.");
        }
        return PageResponse.from(
                reviewRepository.findAllByBusiness_Id(businessId, pageable)
                        .map(reviewMapper::toResponse)
        );
    }
}
