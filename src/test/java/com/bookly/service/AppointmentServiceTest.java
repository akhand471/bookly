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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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
class AppointmentServiceTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private BookableServiceRepository serviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppointmentMapper appointmentMapper;
    @Mock private AvailabilityService availabilityService;

    @InjectMocks
    private AppointmentService appointmentService;

    private UUID businessId;
    private UUID staffId;
    private UUID serviceId;
    private UUID customerId;
    private UUID appointmentId;

    private Business business;
    private BookableService service;
    private User staff;
    private User customer;
    private Appointment appointment;
    private AppointmentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        businessId = UUID.randomUUID();
        staffId = UUID.randomUUID();
        serviceId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        appointmentId = UUID.randomUUID();

        TenantContext.setCurrentTenant(businessId);

        business = Business.builder().id(businessId).name("Salon").subdomain("salon").build();

        service = BookableService.builder()
                .id(serviceId).business(business).name("Haircut")
                .durationMinutes(30).price(new BigDecimal("25.00")).isActive(true).build();

        staff = User.builder().id(staffId).firstName("Jane").lastName("Doe")
                .email("jane@salon.com").role(Role.EMPLOYEE).business(business).build();

        customer = User.builder().id(customerId).firstName("Alex").lastName("Smith")
                .email("alex@customer.com").role(Role.CUSTOMER).business(business).build();

        appointment = Appointment.builder()
                .id(appointmentId).business(business).service(service)
                .staff(staff).customer(customer)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).plusMinutes(30))
                .status(AppointmentStatus.PENDING).build();

        sampleResponse = AppointmentResponse.builder()
                .id(appointmentId).status("PENDING").build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── create ───────────────────────────────────────────────────────────

    @Test
    void create_ShouldPersistAndReturn_WhenSlotIsFree() {
        // Arrange
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .serviceId(serviceId).staffId(staffId)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .build();

        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(service));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(appointmentRepository.existsOverlappingAppointment(any(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.save(any())).thenReturn(appointment);
        when(appointmentMapper.toResponse(appointment)).thenReturn(sampleResponse);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());

        // Act
        AppointmentResponse result = appointmentService.create(request, customerId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(appointmentId);
        verify(appointmentRepository).save(any(Appointment.class));
        verify(availabilityService).evictCache(any(), any(), any(), any());
    }

    @Test
    void create_ShouldThrowConflict_WhenSlotAlreadyTaken() {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .serviceId(serviceId).staffId(staffId)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .build();

        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(service));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(appointmentRepository.existsOverlappingAppointment(any(), any(), any()))
                .thenReturn(true); // slot is taken

        assertThatThrownBy(() -> appointmentService.create(request, customerId))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not available");

        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void create_ShouldThrowResourceNotFound_WhenServiceNotInTenant() {
        CreateAppointmentRequest request = CreateAppointmentRequest.builder()
                .serviceId(serviceId).staffId(staffId)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .build();

        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentService.create(request, customerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── cancel ───────────────────────────────────────────────────────────

    @Test
    void cancel_ShouldSetStatusToCancelled_WhenPending() {
        AppointmentResponse cancelledResponse = AppointmentResponse.builder()
                .id(appointmentId).status("CANCELLED").build();

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponse(appointment)).thenReturn(cancelledResponse);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());

        AppointmentResponse result = appointmentService.cancel(appointmentId);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(availabilityService).evictCache(any(), any(), any(), any());
    }

    @Test
    void cancel_ShouldThrowBadRequest_WhenAlreadyCancelled() {
        appointment.setStatus(AppointmentStatus.CANCELLED);
        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.cancel(appointmentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already cancelled");
    }

    @Test
    void cancel_ShouldThrowBadRequest_WhenCompleted() {
        appointment.setStatus(AppointmentStatus.COMPLETED);
        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.cancel(appointmentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("completed");
    }

    // ─── reschedule ───────────────────────────────────────────────────────

    @Test
    void reschedule_ShouldUpdateTimesAndEvictCache_WhenSlotFree() {
        OffsetDateTime newStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2);
        RescheduleAppointmentRequest request = RescheduleAppointmentRequest.builder()
                .newStartTime(newStart).build();

        AppointmentResponse rescheduledResponse = AppointmentResponse.builder()
                .id(appointmentId).status("PENDING").build();

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsOverlappingAppointment(any(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponse(appointment)).thenReturn(rescheduledResponse);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());

        AppointmentResponse result = appointmentService.reschedule(appointmentId, request);

        assertThat(appointment.getStartTime()).isEqualTo(newStart);
        assertThat(appointment.getEndTime()).isEqualTo(newStart.plusMinutes(30));
        // Cache evicted twice: once for old slot, once for new slot
        verify(availabilityService, times(2)).evictCache(any(), any(), any(), any());
    }

    @Test
    void reschedule_ShouldThrowConflict_WhenNewSlotTaken() {
        OffsetDateTime newStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3);
        RescheduleAppointmentRequest request = RescheduleAppointmentRequest.builder()
                .newStartTime(newStart).build();

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.existsOverlappingAppointment(any(), any(), any()))
                .thenReturn(true);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());

        assertThatThrownBy(() -> appointmentService.reschedule(appointmentId, request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not available");
    }

    @Test
    void reschedule_ShouldThrowBadRequest_WhenAppointmentIsCancelled() {
        appointment.setStatus(AppointmentStatus.CANCELLED);
        RescheduleAppointmentRequest request = RescheduleAppointmentRequest.builder()
                .newStartTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2)).build();

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.reschedule(appointmentId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Cannot reschedule");
    }

    // ─── list ─────────────────────────────────────────────────────────────

    @Test
    void list_ShouldReturnPagedResults() {
        Page<Appointment> page = new PageImpl<>(List.of(appointment));
        when(appointmentRepository.findFiltered(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(page);
        when(appointmentMapper.toResponse(appointment)).thenReturn(sampleResponse);

        PageResponse<AppointmentResponse> result = appointmentService.list(
                null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
}
