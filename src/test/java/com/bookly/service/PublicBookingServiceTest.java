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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class PublicBookingServiceTest {

    @Mock private BusinessService businessService;
    @Mock private BookableServiceRepository serviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private CustomerService customerService;
    @Mock private AvailabilityService availabilityService;
    @Mock private NotificationService notificationService;
    @Mock private AppointmentMapper appointmentMapper;

    @InjectMocks
    private PublicBookingService publicBookingService;

    private UUID businessId;
    private UUID serviceId;
    private UUID staffId;
    private UUID customerId;

    private Business business;
    private BookableService bookableService;
    private User staff;
    private Customer customer;

    @BeforeEach
    void setUp() {
        businessId  = UUID.randomUUID();
        serviceId   = UUID.randomUUID();
        staffId     = UUID.randomUUID();
        customerId  = UUID.randomUUID();

        business = Business.builder()
                .id(businessId)
                .name("Barber Shop")
                .subdomain("barber")
                .isActive(true)
                .cancellationNoticeHours(2)
                .build();

        bookableService = BookableService.builder()
                .id(serviceId)
                .business(business)
                .name("Haircut")
                .durationMinutes(30)
                .price(new BigDecimal("25.00"))
                .isActive(true)
                .build();

        staff = User.builder()
                .id(staffId)
                .business(business)
                .firstName("Jane")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .isEnabled(true)
                .build();

        customer = Customer.builder()
                .id(customerId)
                .business(business)
                .firstName("Alex")
                .lastName("Smith")
                .email("alex@example.com")
                .phone("+1-555-0100")
                .build();
    }

    // ─── getPublicServices ─────────────────────────────────────────────────

    @Test
    void getPublicServices_returnsActiveServices() {
        when(businessService.getBusinessBySubdomain("barber")).thenReturn(business);
        when(serviceRepository.findAllByBusiness_IdAndIsActiveTrue(businessId))
                .thenReturn(List.of(bookableService));

        List<PublicServiceResponse> result = publicBookingService.getPublicServices("barber");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Haircut");
    }

    @Test
    void getPublicServices_unknownSubdomain_throws404() {
        when(businessService.getBusinessBySubdomain("unknown"))
                .thenThrow(new ResourceNotFoundException("No active business found for subdomain: unknown"));

        assertThatThrownBy(() -> publicBookingService.getPublicServices("unknown"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── createGuestBooking ────────────────────────────────────────────────

    @Test
    void createGuestBooking_validRequest_createsAppointmentAndFiresEmail() {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3);

        GuestBookingRequest request = GuestBookingRequest.builder()
                .serviceId(serviceId)
                .staffId(staffId)
                .startTime(start)
                .customerFirstName("Alex")
                .customerLastName("Smith")
                .customerEmail("alex@example.com")
                .customerPhone("+1-555-0100")
                .build();

        when(businessService.getBusinessBySubdomain("barber")).thenReturn(business);
        when(customerService.findOrCreateGuest(any(), eq("Alex"), eq("Smith"),
                eq("alex@example.com"), eq("+1-555-0100"))).thenReturn(customer);
        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(bookableService));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(appointmentRepository.existsOverlappingAppointment(any(), any(), any())).thenReturn(false);

        Appointment savedAppointment = Appointment.builder()
                .id(UUID.randomUUID())
                .business(business)
                .service(bookableService)
                .staff(staff)
                .customer(customer)
                .startTime(start)
                .endTime(start.plusMinutes(30))
                .status(AppointmentStatus.PENDING)
                .build();
        when(appointmentRepository.save(any(Appointment.class))).thenReturn(savedAppointment);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());
        doNothing().when(notificationService).sendBookingConfirmation(any());

        PublicBookingResponse response = publicBookingService.createGuestBooking("barber", request);

        assertThat(response.getServiceName()).isEqualTo("Haircut");
        assertThat(response.getCustomerEmail()).isEqualTo("alex@example.com");
        verify(notificationService).sendBookingConfirmation(savedAppointment);
    }

    @Test
    void createGuestBooking_slotTaken_throwsConflict() {
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3);

        GuestBookingRequest request = GuestBookingRequest.builder()
                .serviceId(serviceId)
                .staffId(staffId)
                .startTime(start)
                .customerFirstName("Alex")
                .customerLastName("Smith")
                .customerEmail("alex@example.com")
                .build();

        when(businessService.getBusinessBySubdomain("barber")).thenReturn(business);
        when(customerService.findOrCreateGuest(any(), any(), any(), any(), any())).thenReturn(customer);
        when(serviceRepository.findByIdAndBusiness_Id(serviceId, businessId))
                .thenReturn(Optional.of(bookableService));
        when(userRepository.findById(staffId)).thenReturn(Optional.of(staff));
        when(appointmentRepository.existsOverlappingAppointment(any(), any(), any())).thenReturn(true);

        assertThatThrownBy(() -> publicBookingService.createGuestBooking("barber", request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not available");
    }

    // ─── cancelPublicBooking ───────────────────────────────────────────────

    @Test
    void cancelPublicBooking_wrongEmail_throwsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        OffsetDateTime futureStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);

        Appointment appointment = Appointment.builder()
                .id(bookingId)
                .business(business)
                .service(bookableService)
                .staff(staff)
                .customer(customer) // email = "alex@example.com"
                .startTime(futureStart)
                .endTime(futureStart.plusMinutes(30))
                .status(AppointmentStatus.PENDING)
                .build();

        when(appointmentRepository.findById(bookingId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> publicBookingService.cancelPublicBooking(bookingId, "wrong@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Email does not match");
    }

    @Test
    void cancelPublicBooking_pastCancellationDeadline_throwsBadRequest() {
        UUID bookingId = UUID.randomUUID();
        // Start time is 1 hour from now, but policy requires 2 hours notice
        OffsetDateTime nearStart = OffsetDateTime.now(ZoneOffset.UTC).plusHours(1);

        Appointment appointment = Appointment.builder()
                .id(bookingId)
                .business(business) // cancellationNoticeHours = 2
                .service(bookableService)
                .staff(staff)
                .customer(customer)
                .startTime(nearStart)
                .endTime(nearStart.plusMinutes(30))
                .status(AppointmentStatus.PENDING)
                .build();

        when(appointmentRepository.findById(bookingId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> publicBookingService.cancelPublicBooking(bookingId, "alex@example.com"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least 2 hour(s)");
    }

    @Test
    void cancelPublicBooking_withinPolicy_succeeds() {
        UUID bookingId = UUID.randomUUID();
        // Start time is 5 days from now — well within the 2-hour policy
        OffsetDateTime futureStart = OffsetDateTime.now(ZoneOffset.UTC).plusDays(5);

        Appointment appointment = Appointment.builder()
                .id(bookingId)
                .business(business)
                .service(bookableService)
                .staff(staff)
                .customer(customer)
                .startTime(futureStart)
                .endTime(futureStart.plusMinutes(30))
                .status(AppointmentStatus.PENDING)
                .build();

        when(appointmentRepository.findById(bookingId)).thenReturn(Optional.of(appointment));
        when(appointmentRepository.save(any())).thenReturn(appointment);
        doNothing().when(availabilityService).evictCache(any(), any(), any(), any());
        doNothing().when(notificationService).sendCancellationNotification(any());

        PublicBookingResponse response =
                publicBookingService.cancelPublicBooking(bookingId, "alex@example.com");

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        verify(notificationService).sendCancellationNotification(appointment);
    }
}
