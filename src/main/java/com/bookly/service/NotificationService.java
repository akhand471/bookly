package com.bookly.service;

import com.bookly.entity.Appointment;
import com.bookly.entity.NotificationLog;
import com.bookly.entity.NotificationStatus;
import com.bookly.entity.NotificationType;
import com.bookly.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.OffsetDateTime;

/**
 * Asynchronous email notification service.
 *
 * <h3>Idempotency</h3>
 * Before sending any email, the service checks {@link NotificationLogRepository#alreadySent}
 * against the DB partial unique index.  If a SENT record already exists for the
 * (appointment, type) pair, the send is skipped.  This means retrying a failed
 * async task or calling the method twice will never produce a duplicate email.
 *
 * <h3>Error handling</h3>
 * Mail failures are caught, logged as {@code FAILED} entries in {@code notification_log},
 * and not re-thrown — so a broken SMTP connection never causes the business logic
 * (booking creation, cancel, reschedule) to roll back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailTemplateBuilder templateBuilder;

    @Value("${app.notifications.from-email:noreply@bookly.com}")
    private String fromEmail;

    @Value("${app.notifications.from-name:Bookly}")
    private String fromName;

    // ─── Public send methods (each is @Async) ─────────────────────────────

    @Async("notificationExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendBookingConfirmation(Appointment appointment) {
        send(appointment, NotificationType.BOOKING_CONFIRMATION);
    }

    @Async("notificationExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCancellationNotification(Appointment appointment) {
        send(appointment, NotificationType.CANCELLATION);
    }

    @Async("notificationExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendRescheduleNotification(Appointment appointment) {
        send(appointment, NotificationType.RESCHEDULE_CONFIRMATION);
    }

    @Async("notificationExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendReminderNotification(Appointment appointment) {
        send(appointment, NotificationType.REMINDER_24H);
    }

    // ─── Core send logic ──────────────────────────────────────────────────

    /**
     * Attempts to send a notification email.  Idempotency is checked first;
     * the result (SENT or FAILED) is persisted to {@code notification_log}.
     */
    private void send(Appointment appointment, NotificationType type) {
        String customerEmail = appointment.getCustomer().getEmail();

        // Idempotency guard — skip if already sent successfully
        if (notificationLogRepository.alreadySent(appointment.getId(), type)) {
            log.debug("Notification already sent — skipping: appointmentId={}, type={}",
                    appointment.getId(), type);
            return;
        }

        NotificationLog.NotificationLogBuilder logBuilder = NotificationLog.builder()
                .business(appointment.getBusiness())
                .appointment(appointment)
                .customerEmail(customerEmail)
                .type(type);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(customerEmail);
            helper.setSubject(templateBuilder.subject(type, appointment));
            helper.setText(templateBuilder.body(type, appointment), true);

            mailSender.send(message);

            notificationLogRepository.save(logBuilder
                    .status(NotificationStatus.SENT)
                    .sentAt(OffsetDateTime.now())
                    .build());

            log.info("Notification sent: type={}, appointmentId={}, to={}",
                    type, appointment.getId(), customerEmail);

        } catch (Exception ex) {
            log.error("Failed to send {} notification for appointment {}: {}",
                    type, appointment.getId(), ex.getMessage());

            notificationLogRepository.save(logBuilder
                    .status(NotificationStatus.FAILED)
                    .errorMessage(ex.getMessage())
                    .build());
        }
    }
}
