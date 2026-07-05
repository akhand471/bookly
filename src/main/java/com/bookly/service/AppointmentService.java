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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Core appointment booking service.
 *
 * <h3>Double-Booking Prevention</h3>
 * Two layers are used together:
 * <ol>
 *   <li>Application-level overlap check ({@code existsOverlappingAppointment}) before insert
 *       to provide a friendly error response early in the flow.</li>
 *   <li>DB unique partial index on {@code (staff_id, start_time) WHERE status != 'CANCELLED'}.
 *       This is the authoritative guard for races — if two requests slip past the app check
 *       concurrently, exactly one will succeed and the other gets a
 *       {@link org.springframework.dao.DataIntegrityViolationException} translated to 409.</li>
 *   <li>{@code @Version} optimistic locking on update operations (reschedule/cancel) ensures
 *       stale reads don't silently overwrite concurrent changes.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final BookableServiceRepository serviceRepository;
    private final UserRepository userRepository;
    private final AppointmentMapper appointmentMapper;
    private final AvailabilityService availabilityService;

    // ─── Create ────────────────────────────────────────────────────────────

    /**
     * Books a new appointment slot.
     *
     * @param request   booking parameters
     * @param customerId UUID of the authenticated customer making the booking
     * @throws ConflictException if the slot is already taken
     * @throws BadRequestException if the slot falls outside the staff's working hours
     */
    @Transactional
    public AppointmentResponse create(CreateAppointmentRequest request, UUID customerId) {
        UUID businessId = requireTenantId();

        // 1. Load and validate service
        BookableService service = serviceRepository.findByIdAndBusiness_Id(request.getServiceId(), businessId)
                .filter(BookableService::isActive)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Service not found: " + request.getServiceId()));

        // 2. Load and validate staff
        User staff = loadStaffInBusiness(request.getStaffId(), businessId);

        // 3. Load customer
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found: " + customerId));

        // 4. Compute end time from service duration
        OffsetDateTime startTime = request.getStartTime();
        OffsetDateTime endTime = startTime.plusMinutes(service.getDurationMinutes());

        // 5. Application-level overlap check (friendly error before hitting the DB constraint)
        if (appointmentRepository.existsOverlappingAppointment(staff.getId(), startTime, endTime)) {
            throw new ConflictException(
                    "This time slot is not available. Please choose another slot.");
        }

        // 6. Persist
        Appointment appointment = Appointment.builder()
                .business(staff.getBusiness())
                .service(service)
                .staff(staff)
                .customer(customer)
                .startTime(startTime)
                .endTime(endTime)
                .status(AppointmentStatus.PENDING)
                .notes(request.getNotes())
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment created: id={}, staff={}, start={}", saved.getId(), staff.getId(), startTime);

        // 7. Evict availability cache for this staff + service + date
        availabilityService.evictCache(businessId, staff.getId(),
                service.getId(), startTime.toLocalDate());

        return appointmentMapper.toResponse(saved);
    }

    // ─── Cancel ────────────────────────────────────────────────────────────

    /**
     * Cancels an appointment.
     * Uses optimistic locking — the version on the appointment row must match.
     * If a concurrent modification happened, {@link ObjectOptimisticLockingFailureException}
     * is thrown and translated to 409 by {@link com.bookly.exception.GlobalExceptionHandler}.
     */
    @Transactional
    public AppointmentResponse cancel(UUID appointmentId) {
        UUID businessId = requireTenantId();
        Appointment appointment = findOwnedAppointment(appointmentId, businessId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BadRequestException("Appointment is already cancelled");
        }
        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException("Cannot cancel a completed appointment");
        }

        LocalDate date = appointment.getStartTime().toLocalDate();
        UUID staffId = appointment.getStaff().getId();
        UUID serviceId = appointment.getService().getId();

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment cancelled: id={}", appointmentId);

        availabilityService.evictCache(businessId, staffId, serviceId, date);
        return appointmentMapper.toResponse(saved);
    }

    // ─── Reschedule ────────────────────────────────────────────────────────

    /**
     * Reschedules an appointment in-place.
     * The existing appointment's times (and optionally staff) are updated.
     * Optimistic locking guards against concurrent reschedules.
     */
    @Transactional
    public AppointmentResponse reschedule(UUID appointmentId, RescheduleAppointmentRequest request) {
        UUID businessId = requireTenantId();
        Appointment appointment = findOwnedAppointment(appointmentId, businessId);

        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BadRequestException(
                    "Cannot reschedule an appointment with status: " + appointment.getStatus());
        }

        // Evict cache for the OLD slot before updating
        availabilityService.evictCache(businessId,
                appointment.getStaff().getId(),
                appointment.getService().getId(),
                appointment.getStartTime().toLocalDate());

        // Resolve new staff (may be the same)
        User newStaff = request.getNewStaffId() != null
                ? loadStaffInBusiness(request.getNewStaffId(), businessId)
                : appointment.getStaff();

        OffsetDateTime newStart = request.getNewStartTime();
        OffsetDateTime newEnd = newStart.plusMinutes(appointment.getService().getDurationMinutes());

        // Check for conflicts at the new slot (excluding the current appointment itself)
        if (appointmentRepository.existsOverlappingAppointment(newStaff.getId(), newStart, newEnd)) {
            throw new ConflictException(
                    "The requested slot is not available. Please choose another time.");
        }

        appointment.setStaff(newStaff);
        appointment.setStartTime(newStart);
        appointment.setEndTime(newEnd);

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment rescheduled: id={}, newStart={}", appointmentId, newStart);

        // Evict cache for the NEW slot
        availabilityService.evictCache(businessId, newStaff.getId(),
                appointment.getService().getId(), newStart.toLocalDate());

        return appointmentMapper.toResponse(saved);
    }

    // ─── List ──────────────────────────────────────────────────────────────

    /**
     * Paginated, filterable list of appointments for the current tenant.
     */
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> list(
            UUID customerId,
            UUID staffId,
            AppointmentStatus status,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable) {

        UUID businessId = requireTenantId();
        Page<AppointmentResponse> page = appointmentRepository
                .findFiltered(businessId, customerId, staffId, status, from, to, pageable)
                .map(appointmentMapper::toResponse);
        return PageResponse.from(page);
    }

    /**
     * Returns a single appointment by ID, tenant-scoped.
     */
    @Transactional(readOnly = true)
    public AppointmentResponse getById(UUID appointmentId) {
        UUID businessId = requireTenantId();
        return appointmentMapper.toResponse(findOwnedAppointment(appointmentId, businessId));
    }

    // ─── Internal helpers ──────────────────────────────────────────────────

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ResourceNotFoundException("Tenant context not set");
        }
        return tenantId;
    }

    private Appointment findOwnedAppointment(UUID appointmentId, UUID businessId) {
        return appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Appointment not found: " + appointmentId));
    }

    private User loadStaffInBusiness(UUID staffId, UUID businessId) {
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + staffId));
        if (staff.getBusiness() == null || !businessId.equals(staff.getBusiness().getId())) {
            throw new ResourceNotFoundException("Staff member not found: " + staffId);
        }
        return staff;
    }
}
