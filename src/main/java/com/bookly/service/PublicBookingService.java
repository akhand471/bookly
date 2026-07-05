package com.bookly.service;

import com.bookly.dto.*;
import com.bookly.entity.*;
import com.bookly.exception.BadRequestException;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.AppointmentMapper;
import com.bookly.repository.AppointmentRepository;
import com.bookly.repository.BookableServiceRepository;
import com.bookly.repository.UserRepository;
import com.bookly.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Handles the public (unauthenticated) booking flow.
 *
 * <p>This service intentionally bypasses the Hibernate tenant filter because
 * public requests arrive without a JWT — the tenant context is set manually
 * from the subdomain path parameter using {@link TenantContext#setCurrentTenant}.
 * Each method that needs tenant isolation calls the appropriate repository
 * with an explicit {@code businessId} parameter.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublicBookingService {

    private final BusinessService businessService;
    private final BookableServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerService customerService;
    private final AvailabilityService availabilityService;
    private final NotificationService notificationService;
    private final AppointmentMapper appointmentMapper;

    // ─── Browse ────────────────────────────────────────────────────────────

    /**
     * Lists all active services for the business identified by {@code subdomain}.
     */
    @Transactional(readOnly = true)
    public List<PublicServiceResponse> getPublicServices(String subdomain) {
        Business business = businessService.getBusinessBySubdomain(subdomain);
        return serviceRepository.findAllByBusiness_IdAndIsActiveTrue(business.getId())
                .stream()
                .map(s -> PublicServiceResponse.builder()
                        .id(s.getId())
                        .name(s.getName())
                        .description(s.getDescription())
                        .durationMinutes(s.getDurationMinutes())
                        .price(s.getPrice())
                        .build())
                .toList();
    }

    /**
     * Lists all active staff members for the business identified by {@code subdomain}.
     */
    @Transactional(readOnly = true)
    public List<PublicStaffResponse> getPublicStaff(String subdomain) {
        Business business = businessService.getBusinessBySubdomain(subdomain);
        return userRepository.findAllByBusiness_IdAndIsEnabledTrue(business.getId())
                .stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE || u.getRole() == Role.BUSINESS_OWNER)
                .map(u -> PublicStaffResponse.builder()
                        .id(u.getId())
                        .firstName(u.getFirstName())
                        .lastName(u.getLastName())
                        .build())
                .toList();
    }

    /**
     * Returns availability slots for a business/service/staff/date combination.
     * Delegates to the existing {@link AvailabilityService} after resolving the
     * business from the subdomain.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse getPublicAvailability(
            String subdomain, UUID serviceId, UUID staffId, LocalDate date) {

        Business business = businessService.getBusinessBySubdomain(subdomain);

        // Temporarily set tenant context so AvailabilityService can apply filters
        TenantContext.setCurrentTenant(business.getId());
        try {
            return availabilityService.getAvailability(serviceId, staffId, date);
        } finally {
            TenantContext.clear();
        }
    }

    // ─── Book ──────────────────────────────────────────────────────────────

    /**
     * Creates a guest appointment.
     *
     * <ol>
     *   <li>Resolves the business from the subdomain.</li>
     *   <li>Looks up (or creates) a {@link Customer} record for the guest.</li>
     *   <li>Resolves the requested service and staff (picks first available if staffId is null).</li>
     *   <li>Checks for slot conflicts.</li>
     *   <li>Persists the {@link Appointment}.</li>
     *   <li>Fires an async booking-confirmation email.</li>
     * </ol>
     */
    @Transactional
    public PublicBookingResponse createGuestBooking(String subdomain, GuestBookingRequest request) {
        Business business = businessService.getBusinessBySubdomain(subdomain);

        // 1. Find or create customer
        Customer customer = customerService.findOrCreateGuest(
                business,
                request.getCustomerFirstName(),
                request.getCustomerLastName(),
                request.getCustomerEmail(),
                request.getCustomerPhone());

        // 2. Load service (must belong to this business and be active)
        BookableService service = serviceRepository
                .findByIdAndBusiness_Id(request.getServiceId(), business.getId())
                .filter(BookableService::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found: " + request.getServiceId()));

        // 3. Resolve staff — either the requested one, or the first available
        User staff = resolveStaff(request.getStaffId(), business, service, request.getStartTime());

        // 4. Compute end time
        OffsetDateTime startTime = request.getStartTime();
        OffsetDateTime endTime   = startTime.plusMinutes(service.getDurationMinutes());

        // 5. Conflict check
        if (appointmentRepository.existsOverlappingAppointment(staff.getId(), startTime, endTime)) {
            throw new ConflictException(
                    "The requested time slot is not available. Please choose another slot.");
        }

        // 6. Persist
        Appointment appointment = Appointment.builder()
                .business(business)
                .service(service)
                .staff(staff)
                .customer(customer)
                .startTime(startTime)
                .endTime(endTime)
                .status(AppointmentStatus.PENDING)
                .notes(request.getNotes())
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Guest booking created: id={}, businessId={}, customerEmail={}",
                saved.getId(), business.getId(), customer.getEmail());

        // 7. Evict availability cache for this slot
        availabilityService.evictCache(business.getId(), staff.getId(),
                service.getId(), startTime.toLocalDate());

        // 8. Async confirmation email
        notificationService.sendBookingConfirmation(saved);

        return toPublicResponse(saved);
    }

    // ─── View & Cancel ─────────────────────────────────────────────────────

    /**
     * Returns a public-facing booking summary by appointment ID.
     * No ownership verification — the bookingId itself is the access token.
     */
    @Transactional(readOnly = true)
    public PublicBookingResponse getPublicBooking(UUID bookingId) {
        return appointmentRepository.findById(bookingId)
                .map(this::toPublicResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));
    }

    /**
     * Cancels a booking from the customer side.
     * Ownership is verified by matching the stored customer email to the supplied email.
     * Cancellation policy is enforced (minimum notice hours).
     */
    @Transactional
    public PublicBookingResponse cancelPublicBooking(UUID bookingId, String customerEmail) {
        Appointment appointment = appointmentRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        // Ownership check: email must match
        if (!appointment.getCustomer().getEmail().equalsIgnoreCase(customerEmail)) {
            throw new BadRequestException("Email does not match the booking record.");
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestException("Booking is already cancelled.");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed appointment.");
        }

        // Cancellation policy check
        enforceCancellationPolicy(appointment);

        LocalDate date      = appointment.getStartTime().toLocalDate();
        UUID staffId        = appointment.getStaff().getId();
        UUID serviceId      = appointment.getService().getId();
        UUID businessId     = appointment.getBusiness().getId();

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Public booking cancelled: id={}", bookingId);

        availabilityService.evictCache(businessId, staffId, serviceId, date);
        notificationService.sendCancellationNotification(saved);

        return toPublicResponse(saved);
    }

    // ─── Internal helpers ──────────────────────────────────────────────────

    /**
     * Resolves the staff member for a booking.
     * If {@code staffId} is provided, validates it belongs to this business.
     * If null, picks the first staff member who has an active schedule for the day.
     */
    private User resolveStaff(UUID staffId, Business business,
                               BookableService service, OffsetDateTime startTime) {
        if (staffId != null) {
            User staff = userRepository.findById(staffId)
                    .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + staffId));
            if (staff.getBusiness() == null || !business.getId().equals(staff.getBusiness().getId())) {
                throw new ResourceNotFoundException("Staff member not found: " + staffId);
            }
            return staff;
        }

        // Pick first available staff with a schedule on the requested day
        return userRepository.findAllByBusiness_IdAndIsEnabledTrue(business.getId())
                .stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE || u.getRole() == Role.BUSINESS_OWNER)
                .filter(u -> !appointmentRepository.existsOverlappingAppointment(
                        u.getId(), startTime,
                        startTime.plusMinutes(service.getDurationMinutes())))
                .findFirst()
                .orElseThrow(() -> new ConflictException(
                        "No available staff for the requested time slot."));
    }

    private void enforceCancellationPolicy(Appointment appointment) {
        int noticeHours = appointment.getBusiness().getCancellationNoticeHours();
        if (noticeHours > 0) {
            OffsetDateTime deadline = appointment.getStartTime().minusHours(noticeHours);
            if (OffsetDateTime.now().isAfter(deadline)) {
                throw new BadRequestException(
                        "Cancellations must be made at least " + noticeHours +
                        " hour(s) before the appointment start time.");
            }
        }
    }

    private PublicBookingResponse toPublicResponse(Appointment a) {
        return PublicBookingResponse.builder()
                .bookingId(a.getId())
                .businessName(a.getBusiness().getName())
                .serviceName(a.getService().getName())
                .durationMinutes(a.getService().getDurationMinutes())
                .staffName(a.getStaff().getFirstName() + " " + a.getStaff().getLastName())
                .startTime(a.getStartTime())
                .endTime(a.getEndTime())
                .status(a.getStatus().name())
                .customerEmail(a.getCustomer().getEmail())
                .build();
    }
}
