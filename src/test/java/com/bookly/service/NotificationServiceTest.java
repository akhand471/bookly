package com.bookly.service;

import com.bookly.entity.*;
import com.bookly.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private EmailTemplateBuilder templateBuilder;

    @InjectMocks
    private NotificationService notificationService;

    private Appointment appointment;
    private UUID appointmentId;

    @BeforeEach
    void setUp() {
        // Inject @Value fields manually
        ReflectionTestUtils.setField(notificationService, "fromEmail", "noreply@bookly.com");
        ReflectionTestUtils.setField(notificationService, "fromName", "Bookly");

        appointmentId = UUID.randomUUID();

        Business business = Business.builder()
                .id(UUID.randomUUID()).name("Salon").subdomain("salon").build();

        BookableService service = BookableService.builder()
                .id(UUID.randomUUID()).name("Haircut").durationMinutes(30)
                .price(new BigDecimal("20.00")).isActive(true).business(business).build();

        User staff = User.builder()
                .id(UUID.randomUUID()).firstName("Jane").lastName("Doe")
                .role(Role.EMPLOYEE).isEnabled(true).business(business).build();

        Customer customer = Customer.builder()
                .id(UUID.randomUUID()).business(business)
                .firstName("Alex").lastName("Smith").email("alex@test.com").build();

        appointment = Appointment.builder()
                .id(appointmentId)
                .business(business)
                .service(service)
                .staff(staff)
                .customer(customer)
                .startTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1))
                .endTime(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).plusMinutes(30))
                .status(AppointmentStatus.PENDING)
                .build();
    }

    @Test
    void sendBookingConfirmation_notYetSent_sendsEmailAndLogsSuccess() throws Exception {
        when(notificationLogRepository.alreadySent(appointmentId, NotificationType.BOOKING_CONFIRMATION))
                .thenReturn(false);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateBuilder.subject(eq(NotificationType.BOOKING_CONFIRMATION), any()))
                .thenReturn("Booking Confirmed — Haircut");
        when(templateBuilder.body(eq(NotificationType.BOOKING_CONFIRMATION), any()))
                .thenReturn("<html>Confirmed</html>");
        doNothing().when(mailSender).send(any(MimeMessage.class));

        notificationService.sendBookingConfirmation(appointment);

        verify(mailSender).send(any(MimeMessage.class));

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(logCaptor.getValue().getType()).isEqualTo(NotificationType.BOOKING_CONFIRMATION);
    }

    @Test
    void sendBookingConfirmation_alreadySent_skipsEmail() {
        when(notificationLogRepository.alreadySent(appointmentId, NotificationType.BOOKING_CONFIRMATION))
                .thenReturn(true);

        notificationService.sendBookingConfirmation(appointment);

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(notificationLogRepository, never()).save(any());
    }

    @Test
    void sendBookingConfirmation_mailFails_logsFailedStatus() {
        when(notificationLogRepository.alreadySent(appointmentId, NotificationType.BOOKING_CONFIRMATION))
                .thenReturn(false);

        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateBuilder.subject(any(), any())).thenReturn("Subject");
        when(templateBuilder.body(any(), any())).thenReturn("<html>Body</html>");
        doThrow(new RuntimeException("SMTP connection refused")).when(mailSender).send(any(MimeMessage.class));

        // Should NOT throw — errors are caught and logged
        notificationService.sendBookingConfirmation(appointment);

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(logCaptor.getValue().getErrorMessage()).contains("SMTP connection refused");
    }
}
