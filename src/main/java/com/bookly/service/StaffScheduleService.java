package com.bookly.service;

import com.bookly.dto.*;
import com.bookly.entity.*;
import com.bookly.exception.BadRequestException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.StaffScheduleMapper;
import com.bookly.repository.StaffScheduleOverrideRepository;
import com.bookly.repository.StaffScheduleRepository;
import com.bookly.repository.UserRepository;
import com.bookly.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.bookly.mapper.UserMapper;

/**
 * Manages staff recurring schedules and date-specific overrides.
 * <p>
 * The {@code PUT /staff/{id}/schedule} endpoint is idempotent — it replaces the
 * entire weekly schedule in a single transaction (delete-then-insert).
 * This prevents partial-update confusion where a caller adds Monday but forgets
 * to remove the old Tuesday entry.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StaffScheduleService {

    private final StaffScheduleRepository scheduleRepository;
    private final StaffScheduleOverrideRepository overrideRepository;
    private final UserRepository userRepository;
    private final StaffScheduleMapper mapper;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public List<UserResponse> getStaff(UUID businessId) {
        return userRepository.findAllByBusiness_IdAndIsEnabledTrue(businessId)
                .stream()
                .filter(u -> u.getRole() == Role.EMPLOYEE || u.getRole() == Role.BUSINESS_OWNER)
                .map(userMapper::toResponse)
                .collect(Collectors.toList());
    }

    // ─── Weekly Schedule ───────────────────────────────────────────────────

    /**
     * Idempotently replaces the full weekly schedule for a staff member.
     * All existing rows are deleted and new ones are inserted in a single transaction.
     */
    @Transactional
    public List<ScheduleDayResponse> setSchedule(UUID staffId, SetScheduleRequest request) {
        UUID businessId = requireTenantId();
        User staff = resolveStaff(staffId, businessId);

        // Validate all day entries before deleting the existing schedule
        for (ScheduleDayRequest day : request.getDays()) {
            validateDayRequest(day);
        }

        // Atomic replace: delete old rows, insert new ones
        scheduleRepository.deleteAllByStaff_IdAndBusiness_Id(staffId, businessId);

        List<StaffSchedule> saved = request.getDays().stream()
                .map(dayReq -> buildSchedule(dayReq, staff, staff.getBusiness()))
                .map(scheduleRepository::save)
                .collect(Collectors.toList());

        log.info("Schedule set for staff={}, business={}, days={}", staffId, businessId, saved.size());
        return saved.stream().map(mapper::toResponse).collect(Collectors.toList());
    }

    /**
     * Returns the full weekly schedule for a staff member.
     */
    @Transactional(readOnly = true)
    public List<ScheduleDayResponse> getSchedule(UUID staffId) {
        UUID businessId = requireTenantId();
        // Staff membership in this business is validated by the tenant-scoped query
        List<StaffSchedule> schedules = scheduleRepository.findByStaffAndBusiness(staffId, businessId);
        return schedules.stream().map(mapper::toResponse).collect(Collectors.toList());
    }

    // ─── Date Overrides ────────────────────────────────────────────────────

    /**
     * Creates or replaces a date-specific schedule override for a staff member.
     */
    @Transactional
    public ScheduleOverrideResponse setOverride(UUID staffId, ScheduleOverrideRequest request) {
        UUID businessId = requireTenantId();
        User staff = resolveStaff(staffId, businessId);

        validateOverrideRequest(request);

        // Delete existing override for this date, then create a fresh one
        overrideRepository.deleteByStaff_IdAndBusiness_IdAndOverrideDate(
                staffId, businessId, request.getOverrideDate());

        StaffScheduleOverride override = StaffScheduleOverride.builder()
                .business(staff.getBusiness())
                .staff(staff)
                .overrideDate(request.getOverrideDate())
                .isDayOff(request.isDayOff())
                .startTime(request.isDayOff() ? null : request.getStartTime())
                .endTime(request.isDayOff() ? null : request.getEndTime())
                .build();

        StaffScheduleOverride saved = overrideRepository.save(override);
        log.info("Override set for staff={}, date={}, dayOff={}", staffId, request.getOverrideDate(), request.isDayOff());
        return mapper.toOverrideResponse(saved);
    }

    /**
     * Removes a date-specific override, restoring the recurring schedule for that date.
     */
    @Transactional
    public void deleteOverride(UUID staffId, LocalDate date) {
        UUID businessId = requireTenantId();
        overrideRepository.deleteByStaff_IdAndBusiness_IdAndOverrideDate(staffId, businessId, date);
        log.info("Override deleted for staff={}, date={}", staffId, date);
    }

    /**
     * Lists all overrides for a staff member (used by admin UIs for calendar views).
     */
    @Transactional(readOnly = true)
    public List<ScheduleOverrideResponse> listOverrides(UUID staffId, LocalDate from, LocalDate to) {
        UUID businessId = requireTenantId();
        return overrideRepository
                .findAllByStaff_IdAndBusiness_IdAndOverrideDateBetween(staffId, businessId, from, to)
                .stream()
                .map(mapper::toOverrideResponse)
                .collect(Collectors.toList());
    }

    // ─── Internal helpers ──────────────────────────────────────────────────

    private UUID requireTenantId() {
        UUID tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new ResourceNotFoundException("Tenant context not set — unauthenticated request");
        }
        return tenantId;
    }

    private User resolveStaff(UUID staffId, UUID businessId) {
        // We deliberately query without the Hibernate filter (findById works across tenants)
        // then validate business membership manually for belt-and-suspenders isolation.
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found: " + staffId));
        if (staff.getBusiness() == null || !businessId.equals(staff.getBusiness().getId())) {
            throw new ResourceNotFoundException("Staff member not found: " + staffId);
        }
        return staff;
    }

    private void validateDayRequest(ScheduleDayRequest day) {
        if (day.getDayOfWeek() == null || day.getDayOfWeek() < 1 || day.getDayOfWeek() > 7) {
            throw new BadRequestException("dayOfWeek must be between 1 (MONDAY) and 7 (SUNDAY)");
        }
        if (day.isWorkingDay()) {
            if (day.getStartTime() == null || day.getEndTime() == null) {
                throw new BadRequestException("startTime and endTime are required for working days");
            }
            if (!day.getEndTime().isAfter(day.getStartTime())) {
                throw new BadRequestException("endTime must be after startTime");
            }
            // Validate breaks
            if (day.getBreaks() != null) {
                for (BreakWindowRequest brk : day.getBreaks()) {
                    if (!brk.getBreakEnd().isAfter(brk.getBreakStart())) {
                        throw new BadRequestException("Break end time must be after break start time");
                    }
                    if (brk.getBreakStart().isBefore(day.getStartTime()) ||
                            brk.getBreakEnd().isAfter(day.getEndTime())) {
                        throw new BadRequestException("Break windows must fall within shift hours");
                    }
                }
            }
        }
    }

    private void validateOverrideRequest(ScheduleOverrideRequest req) {
        if (!req.isDayOff()) {
            if (req.getStartTime() == null || req.getEndTime() == null) {
                throw new BadRequestException("startTime and endTime are required when isDayOff=false");
            }
            if (!req.getEndTime().isAfter(req.getStartTime())) {
                throw new BadRequestException("endTime must be after startTime");
            }
        }
    }

    private StaffSchedule buildSchedule(ScheduleDayRequest req, User staff, Business business) {
        DayOfWeek dow = DayOfWeek.of(req.getDayOfWeek());

        StaffSchedule schedule = StaffSchedule.builder()
                .business(business)
                .staff(staff)
                .dayOfWeek(dow)
                .isWorkingDay(req.isWorkingDay())
                .startTime(req.isWorkingDay() ? req.getStartTime() : null)
                .endTime(req.isWorkingDay() ? req.getEndTime() : null)
                .build();

        if (req.isWorkingDay() && req.getBreaks() != null) {
            req.getBreaks().forEach(br -> {
                StaffScheduleBreak brk = StaffScheduleBreak.builder()
                        .schedule(schedule)
                        .breakStart(br.getBreakStart())
                        .breakEnd(br.getBreakEnd())
                        .build();
                schedule.getBreaks().add(brk);
            });
        }
        return schedule;
    }
}
