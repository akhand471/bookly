package com.bookly.repository;

import com.bookly.entity.Appointment;
import com.bookly.entity.NotificationLog;
import com.bookly.entity.NotificationStatus;
import com.bookly.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link NotificationLog} entries.
 *
 * <p>The idempotency guarantee is enforced at the DB layer via a unique partial
 * index ({@code uidx_notification_sent}) on {@code (appointment_id, type)}
 * where {@code status = 'SENT'}.  The {@link #alreadySent} method provides
 * the application-level pre-check before attempting a send.</p>
 */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Returns {@code true} if a SENT log entry already exists for the given
     * appointment and notification type — used to skip duplicate sends.
     */
    boolean existsByAppointment_IdAndTypeAndStatus(
            UUID appointmentId, NotificationType type, NotificationStatus status);

    /**
     * Convenience wrapper around {@link #existsByAppointment_IdAndTypeAndStatus}
     * for the common case of checking SENT status.
     */
    default boolean alreadySent(UUID appointmentId, NotificationType type) {
        return existsByAppointment_IdAndTypeAndStatus(appointmentId, type, NotificationStatus.SENT);
    }

    /**
     * Finds CONFIRMED or PENDING appointments whose start time falls in the given
     * window — used by {@link com.bookly.service.ReminderSchedulerService} to
     * determine which reminders are due.
     *
     * <p>This query intentionally bypasses the Hibernate tenant filter because the
     * scheduler runs outside a tenant-scoped HTTP request.  It returns all tenants'
     * appointments so the scheduler can fire reminders across the entire platform.</p>
     */
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status IN ('PENDING', 'CONFIRMED')
              AND a.startTime >= :from
              AND a.startTime <  :to
            """)
    List<Appointment> findAppointmentsForReminder(
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to);
}
