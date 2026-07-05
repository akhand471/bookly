package com.bookly.service;

import com.bookly.entity.*;
import com.bookly.repository.AppointmentRepository;
import com.bookly.repository.StaffScheduleOverrideRepository;
import com.bookly.repository.StaffScheduleRepository;
import com.bookly.repository.BookableServiceRepository;
import com.bookly.repository.UserRepository;
import com.bookly.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AvailabilityServiceTest {

    @Mock private BookableServiceRepository serviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private StaffScheduleRepository scheduleRepository;
    @Mock private StaffScheduleOverrideRepository overrideRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AvailabilityService availabilityService;

    private UUID businessId;
    private UUID staffId;
    private UUID serviceId;
    private Business business;
    private BookableService service;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        TenantContext.setCurrentTenant(businessId);

        business = Business.builder().id(businessId).name("Salon").build();
        service = BookableService.builder()
                .id(serviceId).business(business).name("Haircut")
                .durationMinutes(30).price(new BigDecimal("25.00")).isActive(true).build();

        // Wire Redis mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Slot generation (pure logic, no DB) ──────────────────────────────

    @Test
    void generateSlots_ShouldProduceCorrectSlots_For9To17With30MinService() {
        List<LocalTime> slots = availabilityService.generateSlots(
                LocalTime.of(9, 0), LocalTime.of(17, 0), 30);

        // 9:00 to 16:30 inclusive = 16 slots
        assertThat(slots).hasSize(16);
        assertThat(slots.get(0)).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.get(slots.size() - 1)).isEqualTo(LocalTime.of(16, 30));
    }

    @Test
    void generateSlots_ShouldReturnEmpty_WhenDurationExceedsShift() {
        // 30-min service but only 20-min shift
        List<LocalTime> slots = availabilityService.generateSlots(
                LocalTime.of(9, 0), LocalTime.of(9, 20), 30);
        assertThat(slots).isEmpty();
    }

    // ─── Break overlap filtering ───────────────────────────────────────────

    @Test
    void removeBreakOverlaps_ShouldExcludeSlotsCoveringBreak() {
        StaffScheduleBreak lunch = StaffScheduleBreak.builder()
                .breakStart(LocalTime.of(12, 0))
                .breakEnd(LocalTime.of(13, 0))
                .build();

        List<LocalTime> slots = List.of(
                LocalTime.of(11, 30), // overlaps lunch (11:30-12:00 is fine, but 12:00 slot would)
                LocalTime.of(12, 0),  // starts AT break — excluded
                LocalTime.of(12, 30), // mid-break — excluded
                LocalTime.of(13, 0)); // starts after break — OK

        List<LocalTime> result = availabilityService.removeBreakOverlaps(slots, List.of(lunch), 30);

        // 11:30 slot: 11:30-12:00, breakStart=12:00 — slotEnd=breakStart, NOT overlapping
        // 12:00 slot: 12:00-12:30, overlaps lunch → excluded
        // 12:30 slot: 12:30-13:00, overlaps lunch → excluded
        // 13:00 slot: 13:00-13:30, after lunch → kept
        assertThat(result).containsExactly(LocalTime.of(11, 30), LocalTime.of(13, 0));
    }

    // ─── Appointment overlap filtering ────────────────────────────────────

    @Test
    void removeAppointmentOverlaps_ShouldExcludeSlotsOverlappingExistingAppointments() {
        LocalDate date = LocalDate.of(2025, 8, 15);

        Appointment existing = Appointment.builder()
                .startTime(OffsetDateTime.of(date.atTime(10, 0), ZoneOffset.UTC))
                .endTime(OffsetDateTime.of(date.atTime(10, 30), ZoneOffset.UTC))
                .build();

        List<LocalTime> slots = List.of(
                LocalTime.of(9, 30),   // 9:30-10:00: no overlap → kept
                LocalTime.of(10, 0),   // 10:00-10:30: exactly matches → excluded
                LocalTime.of(10, 30)); // 10:30-11:00: starts after → kept

        List<LocalTime> result = availabilityService
                .removeAppointmentOverlaps(slots, List.of(existing), date, 30);

        assertThat(result).containsExactly(LocalTime.of(9, 30), LocalTime.of(10, 30));
    }

    // ─── Full availability query ───────────────────────────────────────────

    @Test
    void getAvailability_ShouldReturnSlots_WhenCacheMissAndScheduleExists() {
        LocalDate date = LocalDate.of(2025, 8, 15); // Friday

        // Cache miss
        when(valueOperations.get(anyString())).thenReturn(null);
        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(service));

        // No override
        when(overrideRepository.findByStaff_IdAndBusiness_IdAndOverrideDate(staffId, businessId, date))
                .thenReturn(Optional.empty());

        // Weekly schedule for Friday (DayOfWeek.FRIDAY = 5)
        StaffSchedule schedule = StaffSchedule.builder()
                .dayOfWeek(java.time.DayOfWeek.FRIDAY)
                .isWorkingDay(true)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(12, 0)) // only 3-hour shift → 6 slots
                .build();
        when(scheduleRepository.findByStaffAndBusinessAndDay(staffId, businessId, java.time.DayOfWeek.FRIDAY))
                .thenReturn(Optional.of(schedule));

        // No existing appointments
        when(appointmentRepository.findActiveByStaffAndDay(eq(staffId), eq(businessId), any(), any()))
                .thenReturn(List.of());

        // Act
        var response = availabilityService.getAvailability(serviceId, staffId, date);

        // Assert
        assertThat(response.getAvailableSlots()).hasSize(6); // 9:00,9:30,10:00,10:30,11:00,11:30
        assertThat(response.getAvailableSlots().get(0)).isEqualTo(LocalTime.of(9, 0));
        assertThat(response.getServiceName()).isEqualTo("Haircut");
        verify(valueOperations).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void getAvailability_ShouldReturnEmpty_WhenStaffHasDayOff() {
        LocalDate date = LocalDate.of(2025, 12, 25);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(service));

        StaffScheduleOverride dayOff = StaffScheduleOverride.builder()
                .isDayOff(true).overrideDate(date).build();
        when(overrideRepository.findByStaff_IdAndBusiness_IdAndOverrideDate(staffId, businessId, date))
                .thenReturn(Optional.of(dayOff));

        var response = availabilityService.getAvailability(serviceId, staffId, date);

        assertThat(response.getAvailableSlots()).isEmpty();
    }
}
