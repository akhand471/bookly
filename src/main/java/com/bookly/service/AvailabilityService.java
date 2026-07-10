package com.bookly.service;

import com.bookly.dto.AvailabilityResponse;
import com.bookly.entity.*;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.repository.AppointmentRepository;
import com.bookly.repository.BookableServiceRepository;
import com.bookly.repository.StaffScheduleOverrideRepository;
import com.bookly.repository.StaffScheduleRepository;
import com.bookly.security.TenantContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Availability Engine — computes open booking slots for a service + staff + date.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li>Check Redis cache ({@code avail:{businessId}:{staffId}:{serviceId}:{date}}).
 *       Return cached result if present.</li>
 *   <li>Resolve the effective schedule for the date:
 *       check for a {@link StaffScheduleOverride} first, fall back to the recurring
 *       {@link StaffSchedule} for the same day-of-week.</li>
 *   <li>If the employee is off or has no schedule, return an empty slot list.</li>
 *   <li>Generate candidate slots from shiftStart to (shiftEnd - serviceDuration) at
 *       {@code serviceDuration}-minute intervals.</li>
 *   <li>Filter out slots that overlap any break window.</li>
 *   <li>Fetch all active appointments for the staff on that day (single query, no N+1).
 *       Filter out slots that overlap any existing appointment.</li>
 *   <li>Cache the result in Redis with a 5-minute TTL and return.</li>
 * </ol>
 *
 * <h3>Cache Invalidation</h3>
 * The {@link AppointmentService} calls {@link #evictCache} on every
 * create / cancel / reschedule to keep availability stale for at most 5 minutes
 * even without an explicit eviction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AvailabilityService {

    private static final long CACHE_TTL_MINUTES = 5;
    private static final String CACHE_KEY_PREFIX = "avail:";

    private final BookableServiceRepository serviceRepository;
    private final StaffScheduleRepository scheduleRepository;
    private final StaffScheduleOverrideRepository overrideRepository;
    private final AppointmentRepository appointmentRepository;
    private final StringRedisTemplate redisTemplate;

    // Shared ObjectMapper configured with JSR-310 (LocalTime/LocalDate) support
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ─── Public API ────────────────────────────────────────────────────────

    /**
     * Returns available time slots for a service + staff member on a specific date.
     *
     * @param serviceId the bookable service UUID
     * @param staffId   the staff member UUID
     * @param date      the date to query
     * @return response containing ordered list of available {@link LocalTime} slot starts
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID serviceId, UUID staffId, LocalDate date) {
        UUID businessId = requireTenantId();

        // 1. Try cache first
        String cacheKey = buildCacheKey(businessId, staffId, serviceId, date);
        List<LocalTime> cached = readFromCache(cacheKey);
        if (cached != null) {
            log.debug("Availability cache hit: key={}", cacheKey);
            BookableService svc = loadService(serviceId, businessId);
            return buildResponse(serviceId, svc, staffId, date, cached);
        }

        // 2. Load service and validate tenant ownership
        BookableService svc = loadService(serviceId, businessId);

        // 3. Resolve effective schedule for the date
        EffectiveSchedule schedule = resolveSchedule(staffId, businessId, date);
        if (!schedule.isWorking()) {
            log.debug("Staff {} has no schedule for {}", staffId, date);
            writeToCache(cacheKey, List.of());
            return buildResponse(serviceId, svc, staffId, date, List.of());
        }

        // 4. Generate candidate slots
        List<LocalTime> slots = generateSlots(
                schedule.startTime(), schedule.endTime(), svc.getDurationMinutes());

        // 5. Remove break-window overlaps
        slots = removeBreakOverlaps(slots, schedule.breaks(), svc.getDurationMinutes());

        // 6. Remove existing appointment overlaps (single DB query, no N+1)
        ZoneId utcZone = ZoneId.of("UTC");
        OffsetDateTime dayStart = date.atStartOfDay(utcZone).toOffsetDateTime();
        OffsetDateTime dayEnd = date.plusDays(1).atStartOfDay(utcZone).toOffsetDateTime();

        List<Appointment> existing = appointmentRepository
                .findActiveByStaffAndDay(staffId, businessId, dayStart, dayEnd);

        slots = removeAppointmentOverlaps(slots, existing, date, svc.getDurationMinutes());

        // 7. Cache and return
        writeToCache(cacheKey, slots);
        log.debug("Availability computed for staff={}, date={}, slots={}", staffId, date, slots.size());
        return buildResponse(serviceId, svc, staffId, date, slots);
    }

    /**
     * Evicts the availability cache for a specific staff + service + date combination.
     * Called by {@link AppointmentService} on every booking state change.
     */
    public void evictCache(UUID businessId, UUID staffId, UUID serviceId, LocalDate date) {
        String key = buildCacheKey(businessId, staffId, serviceId, date);
        redisTemplate.delete(key);
        log.debug("Availability cache evicted: key={}", key);
    }

    // ─── Slot Generation ───────────────────────────────────────────────────

    /**
     * Generates candidate slot start times from {@code workStart} to
     * {@code workEnd - durationMinutes} at {@code durationMinutes} intervals.
     */
    List<LocalTime> generateSlots(LocalTime workStart, LocalTime workEnd, int durationMinutes) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime cursor = workStart;
        LocalTime latestStart = workEnd.minusMinutes(durationMinutes);
        while (!cursor.isAfter(latestStart)) {
            slots.add(cursor);
            cursor = cursor.plusMinutes(durationMinutes);
        }
        return slots;
    }

    /**
     * Removes any slot whose [slotStart, slotStart + duration) window overlaps a break.
     */
    List<LocalTime> removeBreakOverlaps(
            List<LocalTime> slots,
            List<StaffScheduleBreak> breaks,
            int durationMinutes) {
        if (breaks == null || breaks.isEmpty()) return slots;
        return slots.stream()
                .filter(slot -> breaks.stream().noneMatch(brk -> overlapsBreak(slot, durationMinutes, brk)))
                .toList();
    }

    /**
     * Removes any slot whose [slotStart, slotStart + duration) window overlaps
     * an existing appointment.
     */
    List<LocalTime> removeAppointmentOverlaps(
            List<LocalTime> slots,
            List<Appointment> appointments,
            LocalDate date,
            int durationMinutes) {
        if (appointments == null || appointments.isEmpty()) return slots;
        return slots.stream()
                .filter(slot -> appointments.stream()
                        .noneMatch(appt -> overlapsAppointment(slot, durationMinutes, appt, date)))
                .toList();
    }

    // ─── Redis Cache ───────────────────────────────────────────────────────

    private String buildCacheKey(UUID businessId, UUID staffId, UUID serviceId, LocalDate date) {
        return CACHE_KEY_PREFIX + businessId + ":" + staffId + ":" + serviceId + ":" + date;
    }

    private List<LocalTime> readFromCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return null;
            return objectMapper.readValue(json, new TypeReference<List<LocalTime>>() {});
        } catch (Exception e) {
            log.warn("Failed to read availability from cache: {}", e.getMessage());
            return null;
        }
    }

    private void writeToCache(String key, List<LocalTime> slots) {
        try {
            String json = objectMapper.writeValueAsString(slots);
            redisTemplate.opsForValue().set(key, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to write availability to cache: {}", e.getMessage());
            // Non-fatal — availability will still be computed on next request
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ResourceNotFoundException("Tenant context not set");
        }
        return tenantId;
    }

    private BookableService loadService(UUID serviceId, UUID businessId) {
        return serviceRepository.findByIdAndBusiness_Id(serviceId, businessId)
                .filter(BookableService::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + serviceId));
    }

    /**
     * Resolves the effective schedule: override takes precedence over recurring.
     */
    private EffectiveSchedule resolveSchedule(UUID staffId, UUID businessId, LocalDate date) {
        // Check override first
        Optional<StaffScheduleOverride> override =
                overrideRepository.findByStaff_IdAndBusiness_IdAndOverrideDate(staffId, businessId, date);

        if (override.isPresent()) {
            StaffScheduleOverride o = override.get();
            if (o.isDayOff()) return EffectiveSchedule.off();
            return EffectiveSchedule.custom(o.getStartTime(), o.getEndTime());
        }

        // Fall back to recurring weekly schedule
        DayOfWeek dow = date.getDayOfWeek();
        return scheduleRepository.findByStaffAndBusinessAndDay(staffId, businessId, dow)
                .map(s -> s.isWorkingDay()
                        ? EffectiveSchedule.recurring(s.getStartTime(), s.getEndTime(), s.getBreaks())
                        : EffectiveSchedule.off())
                .orElse(EffectiveSchedule.off());
    }

    private boolean overlapsBreak(LocalTime slotStart, int durationMinutes, StaffScheduleBreak brk) {
        LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
        // Overlap when: slotStart < breakEnd AND slotEnd > breakStart
        return slotStart.isBefore(brk.getBreakEnd()) && slotEnd.isAfter(brk.getBreakStart());
    }

    private boolean overlapsAppointment(LocalTime slotStart, int durationMinutes,
                                         Appointment appt, LocalDate date) {
        LocalTime slotEnd = slotStart.plusMinutes(durationMinutes);
        LocalTime apptStart = appt.getStartTime().atZoneSameInstant(ZoneOffset.UTC)
                .toLocalTime();
        LocalTime apptEnd = appt.getEndTime().atZoneSameInstant(ZoneOffset.UTC)
                .toLocalTime();
        return slotStart.isBefore(apptEnd) && slotEnd.isAfter(apptStart);
    }

    private AvailabilityResponse buildResponse(UUID serviceId, BookableService svc,
                                                UUID staffId, LocalDate date, List<LocalTime> slots) {
        return AvailabilityResponse.builder()
                .serviceId(serviceId)
                .serviceName(svc.getName())
                .durationMinutes(svc.getDurationMinutes())
                .staffId(staffId)
                .date(date)
                .availableSlots(slots)
                .build();
    }

    /**
     * Value record holding the effective working hours for a given date.
     * Using a record to avoid mutable state in the computation pipeline.
     */
    private record EffectiveSchedule(
            boolean isWorking,
            LocalTime startTime,
            LocalTime endTime,
            List<StaffScheduleBreak> breaks) {

        static EffectiveSchedule off() {
            return new EffectiveSchedule(false, null, null, List.of());
        }

        static EffectiveSchedule recurring(LocalTime start, LocalTime end,
                                            List<StaffScheduleBreak> breaks) {
            return new EffectiveSchedule(true, start, end, breaks != null ? breaks : List.of());
        }

        static EffectiveSchedule custom(LocalTime start, LocalTime end) {
            return new EffectiveSchedule(true, start, end, List.of());
        }
    }
}
