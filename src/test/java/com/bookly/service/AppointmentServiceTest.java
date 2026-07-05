package com.bookly.service;

import com.bookly.dto.*;
import com.bookly.entity.*;
import com.bookly.exception.BadRequestException;
import com.bookly.exception.ConflictException;
import com.bookly.exception.ResourceNotFoundException;
import com.bookly.mapper.AppointmentMapper;
import com.bookly.repository.AppointmentRepository;
import com.bookly.repository.BookableServiceRepository;
import com.bookly.repository.CustomerRepository;
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
    @Mock private CustomerRepository customerRepository;
    @Mock private AppointmentMapper appointmentMapper;
    @Mock private AvailabilityService availabilityService;
    @Mock private NotificationService notificationService;

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

        business = Business.builder().id(businessId).name("Salon").subdomain("salon")
                .cancellationNoticeHours(2).build();

        service = BookableService.builder()
                .id(serviceId).business(business).name("Haircut")
                .durationMinutes(30).price(new BigDecimal("25.00")).isActive(true).build();

        staff = User.builder().id(staffId).firstName("Jane").lastName("Doe")
                .email("jane@salon.com").role(Role.EMPLOYEE).business(business).build();

        // Phase 3: customer is now a Customer entity (not User)
        com.bookly.entity.Customer customerEntity = com.bookly.entity.Customer.builder()
                .id(customerId).business(business)
                .firstName("Alex").lastName("Smith").email("alex@customer.com").build();

        appointment = Appointment.builder()
                .id(appointmentId).business(business).service(service)
                .staff(staff).customer(customerEntity)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5).plusMinutes(30))
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
        when(customerRepository.findByIdAndBusiness_Id(customerId, businessId))
                .thenReturn(Optional.of(com.bookly.entity.Customer.builder()
                        .id(customerId).business(business)
                        .firstName("Alex").lastName("Smith").email("alex@customer.com").build()));
        when(appointmentRepository.existsOverlappingAppointment(any(), any(), any()))
                .thenReturn(false);
        when(appointmentRepository.save(any())).thenReturn(appointment);
        when(appointmentMapper.toResponse(appointment)).thenReturn(sampleResponse);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());
        doNothing().when(notificationService).sendBookingConfirmation(any());

        // Act
        AppointmentResponse result = appointmentService.create(request, customerId);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(appointmentId);
        verify(appointmentRepository).save(any(Appointment.class));
        verify(availabilityService).evictCache(any(), any(), any(), any());
        verify(notificationService).sendBookingConfirmation(any());
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
        when(customerRepository.findByIdAndBusiness_Id(customerId, businessId))
                .thenReturn(Optional.of(com.bookly.entity.Customer.builder()
                        .id(customerId).business(business)
                        .firstName("Alex").lastName("Smith").email("alex@customer.com").build()));
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
        doNothing().when(notificationService).sendCancellationNotification(any());

        AppointmentResponse result = appointmentService.cancel(appointmentId);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(result.getStatus()).isEqualTo("CANCELLED");
        verify(availabilityService).evictCache(any(), any(), any(), any());
        verify(notificationService).sendCancellationNotification(appointment);
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
        doNothing().when(notificationService).sendRescheduleNotification(any());

        AppointmentResponse result = appointmentService.reschedule(appointmentId, request);

        assertThat(appointment.getStartTime()).isEqualTo(newStart);
        assertThat(appointment.getEndTime()).isEqualTo(newStart.plusMinutes(30));
        // Cache evicted twice: once for old slot, once for new slot
        verify(availabilityService, times(2)).evictCache(any(), any(), any(), any());
        verify(notificationService).sendRescheduleNotification(appointment);
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

    // ─── cancellation policy ───────────────────────────────────────────────

    @Test
    void cancel_ShouldSucceed_WhenBeforeCancellationDeadline() {
        // Appointment is 5 days away — well outside the 2-hour notice window
        appointment.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(5));
        appointment.setEndTime(appointment.getStartTime().plusMinutes(30));

        AppointmentResponse cancelledResponse = AppointmentResponse.builder()
                .id(appointmentId).status("CANCELLED").build();

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponse(appointment)).thenReturn(cancelledResponse);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());
        doNothing().when(notificationService).sendCancellationNotification(any());

        AppointmentResponse result = appointmentService.cancel(appointmentId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancel_ShouldThrowBadRequest_WhenWithinCancellationNoticeWindow() {
        // Business requires 2 hours notice; appointment starts in 1 hour
        appointment.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        appointment.setEndTime(appointment.getStartTime().plusMinutes(30));

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.cancel(appointmentId))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 2 hour(s)");
    }

    @Test
    void cancel_ShouldSucceed_WhenCancellationPolicyIsZero() {
        // Business has no restriction (cancellationNoticeHours = 0)
        Business noPolicyBusiness = Business.builder()
                .id(businessId).name("No Policy Salon").subdomain("nopolicy")
                .cancellationNoticeHours(0).build();

        // Appointment starts in 30 minutes — normally would be blocked
        appointment = Appointment.builder()
                .id(appointmentId).business(noPolicyBusiness).service(service)
                .staff(staff).customer(appointment.getCustomer())
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(60))
                .status(AppointmentStatus.PENDING).build();

        AppointmentResponse cancelledResponse = AppointmentResponse.builder()
                .id(appointmentId).status("CANCELLED").build();

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(appointment)).thenReturn(appointment);
        when(appointmentMapper.toResponse(appointment)).thenReturn(cancelledResponse);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());
        doNothing().when(notificationService).sendCancellationNotification(any());

        AppointmentResponse result = appointmentService.cancel(appointmentId);

        assertThat(result.getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void reschedule_ShouldThrowBadRequest_WhenWithinCancellationNoticeWindow() {
        // Business requires 2 hours notice; appointment starts in 1 hour
        appointment.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        appointment.setEndTime(appointment.getStartTime().plusMinutes(30));
        RescheduleAppointmentRequest request = RescheduleAppointmentRequest.builder()
                .newStartTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(2)).build();

        when(appointmentRepository.findByIdAndBusiness_Id(appointmentId, businessId))
                .thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> appointmentService.reschedule(appointmentId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 2 hour(s)");
    }
}
