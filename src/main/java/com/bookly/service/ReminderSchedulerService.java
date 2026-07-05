package com.bookly.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.bookly.entity.Appointment;
import com.bookly.repository.NotificationLogRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Scheduled service that fires 24-hour reminder emails.
 *
 * <h3>Schedule</h3>
 * Runs every hour (configurable via {@code app.scheduling.reminder-cron}).
 * Each run queries for appointments whose start time falls in the 23–25h window
 * from now — the 2-hour window ensures an appointment is captured even if the
 * scheduler fired slightly early or late relative to the previous run.
 *
 * <h3>Idempotency</h3>
 * {@link NotificationService} checks the {@code notification_log} table before
 * sending.  If a reminder was already sent (e.g. a previous run caught the same
 * appointment), the send is silently skipped.  No duplicate emails will ever be
 * sent regardless of how many times this job fires.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderSchedulerService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationService notificationService;

    /**
     * Fires every hour. Sends reminder emails for all upcoming appointments
     * that start within the next 23–25 hours.
     */
    @Scheduled(cron = "${app.scheduling.reminder-cron:0 0 * * * *}")
    public void sendDueReminders() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime windowStart = now.plusHours(23);
        OffsetDateTime windowEnd   = now.plusHours(25);

        List<Appointment> dueAppointments =
                notificationLogRepository.findAppointmentsForReminder(windowStart, windowEnd);

        if (dueAppointments.isEmpty()) {
            log.debug("Reminder scheduler: no upcoming appointments in window {}-{}", windowStart, windowEnd);
            return;
        }

        log.info("Reminder scheduler: {} appointment(s) due for reminder", dueAppointments.size());
        for (Appointment appointment : dueAppointments) {
            // Dispatch async — NotificationService handles idempotency internally
            notificationService.sendReminderNotification(appointment);
        }
    }
}
