package com.bookly.service;

import com.bookly.entity.Appointment;
import com.bookly.entity.NotificationType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Builds HTML email bodies for each notification type.
 * Kept deliberately simple (no Thymeleaf) to avoid adding template engine dependencies.
 * Replace with Thymeleaf or Freemarker templates if richer formatting is required.
 */
@Component
public class EmailTemplateBuilder {

    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, MMMM d yyyy 'at' h:mm a z");

    @Value("${app.notifications.from-name:Bookly}")
    private String appName;

    /** Builds the subject line for a given notification type. */
    public String subject(NotificationType type, Appointment appointment) {
        String service = appointment.getService().getName();
        return switch (type) {
            case BOOKING_CONFIRMATION   -> "✅ Booking Confirmed — " + service;
            case CANCELLATION           -> "❌ Booking Cancelled — " + service;
            case RESCHEDULE_CONFIRMATION -> "🔄 Booking Rescheduled — " + service;
            case REMINDER_24H           -> "⏰ Reminder: " + service + " tomorrow";
        };
    }

    /** Builds the full HTML body for a given notification type. */
    public String body(NotificationType type, Appointment appointment) {
        String customerName = appointment.getCustomer().getFirstName();
        String serviceName  = appointment.getService().getName();
        String staffName    = appointment.getStaff().getFirstName() + " " + appointment.getStaff().getLastName();
        String businessName = appointment.getBusiness().getName();
        String startFormatted = appointment.getStartTime().format(DISPLAY_FORMAT);
        String endFormatted   = appointment.getEndTime().format(DISPLAY_FORMAT);

        String headlineColor = switch (type) {
            case BOOKING_CONFIRMATION    -> "#22c55e"; // green
            case CANCELLATION            -> "#ef4444"; // red
            case RESCHEDULE_CONFIRMATION -> "#f59e0b"; // amber
            case REMINDER_24H            -> "#3b82f6"; // blue
        };

        String headline = switch (type) {
            case BOOKING_CONFIRMATION    -> "Your booking is confirmed!";
            case CANCELLATION            -> "Your booking has been cancelled.";
            case RESCHEDULE_CONFIRMATION -> "Your booking has been rescheduled.";
            case REMINDER_24H            -> "Your appointment is tomorrow!";
        };

        String bodyText = switch (type) {
            case BOOKING_CONFIRMATION ->
                "Great news, " + customerName + "! Your appointment has been confirmed. See the details below.";
            case CANCELLATION ->
                "Hi " + customerName + ", your appointment has been cancelled. We hope to see you again soon.";
            case RESCHEDULE_CONFIRMATION ->
                "Hi " + customerName + ", your appointment has been rescheduled. Here are your updated details.";
            case REMINDER_24H ->
                "Hi " + customerName + "! Just a friendly reminder that your appointment is coming up tomorrow.";
        };

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>%s</title></head>
                <body style="margin:0;padding:0;background:#f4f4f5;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f5;padding:32px 0;">
                    <tr><td align="center">
                      <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">
                        <!-- Header -->
                        <tr><td style="background:%s;padding:28px 40px;text-align:center;">
                          <h1 style="margin:0;color:#ffffff;font-size:22px;font-weight:700;">%s</h1>
                        </td></tr>
                        <!-- Body -->
                        <tr><td style="padding:36px 40px;">
                          <p style="margin:0 0 24px;color:#374151;font-size:16px;">%s</p>
                          <!-- Detail card -->
                          <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f9fafb;border-radius:8px;padding:20px;">
                            <tr><td style="padding:8px 0;color:#6b7280;font-size:13px;font-weight:600;text-transform:uppercase;letter-spacing:0.05em;">Service</td>
                                <td style="padding:8px 0;color:#111827;font-size:15px;font-weight:600;">%s</td></tr>
                            <tr><td style="padding:8px 0;color:#6b7280;font-size:13px;font-weight:600;text-transform:uppercase;letter-spacing:0.05em;">Staff</td>
                                <td style="padding:8px 0;color:#111827;font-size:15px;">%s</td></tr>
                            <tr><td style="padding:8px 0;color:#6b7280;font-size:13px;font-weight:600;text-transform:uppercase;letter-spacing:0.05em;">Start</td>
                                <td style="padding:8px 0;color:#111827;font-size:15px;">%s</td></tr>
                            <tr><td style="padding:8px 0;color:#6b7280;font-size:13px;font-weight:600;text-transform:uppercase;letter-spacing:0.05em;">End</td>
                                <td style="padding:8px 0;color:#111827;font-size:15px;">%s</td></tr>
                          </table>
                        </td></tr>
                        <!-- Footer -->
                        <tr><td style="background:#f9fafb;padding:20px 40px;text-align:center;border-top:1px solid #e5e7eb;">
                          <p style="margin:0;color:#9ca3af;font-size:13px;">Powered by <strong>%s</strong> &mdash; %s</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(
                headline, headlineColor, headline,
                bodyText,
                serviceName, staffName, startFormatted, endFormatted,
                appName, businessName
        );
    }
}
