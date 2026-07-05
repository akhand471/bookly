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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffScheduleServiceTest {

    @Mock private StaffScheduleRepository scheduleRepository;
    @Mock private StaffScheduleOverrideRepository overrideRepository;
    @Mock private UserRepository userRepository;
    @Mock private StaffScheduleMapper mapper;

    @InjectMocks
    private StaffScheduleService staffScheduleService;

    private UUID businessId;
    private UUID staffId;
    private User staff;
    private Business business;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        TenantContext.setCurrentTenant(businessId);

        business = Business.builder().id(businessId).name("Salon").subdomain("salon").build();
        staff = User.builder().id(staffId).email("staff@salon.com")
                .role(Role.EMPLOYEE).business(business).build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── setSchedule ──────────────────────────────────────────────────────

    @Test
    void setSchedule_ShouldReplaceAndReturnSchedule_WhenValid() {
        // Arrange
        ScheduleDayRequest monday = ScheduleDayRequest.builder()
                .dayOfWeek(1)
                .isWorkingDay(true)
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(17, 0))
                .build();
        SetScheduleRequest req = new SetScheduleRequest(List.of(monday));

        StaffSchedule saved = StaffSchedule.builder()
                .id(UUID.randomUUID()).business(business).staff(staff)
                .dayOfWeek(DayOfWeek.MONDAY)
                .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(17, 0))
                .isWorkingDay(true).build();
        ScheduleDayResponse response = ScheduleDayResponse.builder()
                .dayOfWeek(1).isWorkingDay(true).build();

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        doNothing().when(scheduleRepository).deleteAllByStaff_IdAndBusiness_Id(any(), any());
        when(scheduleRepository.save(any())).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        // Act
        List<ScheduleDayResponse> result = staffScheduleService.setSchedule(staffId, req);

        // Assert
        assertThat(result).hasSize(1);
        verify(scheduleRepository).deleteAllByStaff_IdAndBusiness_Id(staffId, businessId);
        verify(scheduleRepository).save(any(StaffSchedule.class));
    }

    @Test
    void setSchedule_ShouldThrowBadRequest_WhenWorkingDayMissingTimes() {
        ScheduleDayRequest bad = ScheduleDayRequest.builder()
                .dayOfWeek(1).isWorkingDay(true)
                // startTime and endTime intentionally null
                .build();
        SetScheduleRequest req = new SetScheduleRequest(List.of(bad));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffScheduleService.setSchedule(staffId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("startTime and endTime are required");
    }

    @Test
    void setSchedule_ShouldThrowBadRequest_WhenEndTimeNotAfterStart() {
        ScheduleDayRequest bad = ScheduleDayRequest.builder()
                .dayOfWeek(1).isWorkingDay(true)
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(9, 0)) // inverted
                .build();
        SetScheduleRequest req = new SetScheduleRequest(List.of(bad));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffScheduleService.setSchedule(staffId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("endTime must be after startTime");
    }

    @Test
    void setSchedule_ShouldThrowResourceNotFound_WhenStaffBelongsToDifferentBusiness() {
        Business otherBusiness = Business.builder().id(UUID.randomUUID()).name("Other").build();
        User foreignStaff = User.builder().id(staffId).email("x@other.com")
                .role(Role.EMPLOYEE).business(otherBusiness).build();

        when(userRepository.findById(staffId)).thenReturn(Optional.of(foreignStaff));

        SetScheduleRequest req = new SetScheduleRequest(List.of(
                ScheduleDayRequest.builder().dayOfWeek(1).isWorkingDay(false).build()));

        assertThatThrownBy(() -> staffScheduleService.setSchedule(staffId, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── setOverride ──────────────────────────────────────────────────────

    @Test
    void setOverride_ShouldPersistDayOff() {
        ScheduleOverrideRequest req = ScheduleOverrideRequest.builder()
                .overrideDate(LocalDate.now().plusDays(7))
                .isDayOff(true)
                .build();

        StaffScheduleOverride saved = StaffScheduleOverride.builder()
                .id(UUID.randomUUID()).staff(staff).business(business)
                .overrideDate(req.getOverrideDate()).isDayOff(true).build();
        ScheduleOverrideResponse response = ScheduleOverrideResponse.builder()
                .isDayOff(true).build();

        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        doNothing().when(overrideRepository)
                .deleteByStaff_IdAndBusiness_IdAndOverrideDate(any(), any(), any());
        when(overrideRepository.save(any())).thenReturn(saved);
        when(mapper.toOverrideResponse(saved)).thenReturn(response);

        ScheduleOverrideResponse result = staffScheduleService.setOverride(staffId, req);

        assertThat(result.isDayOff()).isTrue();
        verify(overrideRepository).save(any(StaffScheduleOverride.class));
    }

    @Test
    void setOverride_ShouldThrowBadRequest_WhenNotDayOffButMissingTimes() {
        ScheduleOverrideRequest req = ScheduleOverrideRequest.builder()
                .overrideDate(LocalDate.now().plusDays(1))
                .isDayOff(false)
                // startTime and endTime missing
                .build();
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> staffScheduleService.setOverride(staffId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("startTime and endTime are required");
    }

    // ─── deleteOverride ───────────────────────────────────────────────────

    @Test
    void deleteOverride_ShouldCallRepository() {
        LocalDate date = LocalDate.now().plusDays(5);
        doNothing().when(overrideRepository)
                .deleteByStaff_IdAndBusiness_IdAndOverrideDate(staffId, businessId, date);

        staffScheduleService.deleteOverride(staffId, date);

        verify(overrideRepository).deleteByStaff_IdAndBusiness_IdAndOverrideDate(staffId, businessId, date);
    }
}
