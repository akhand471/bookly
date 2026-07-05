package com.bookly.entity;

/**
 * Type of notification sent to a customer.
 *
 * <pre>
 * BOOKING_CONFIRMATION  — sent immediately after a booking is created
 * CANCELLATION          — sent when an appointment is cancelled
 * RESCHEDULE_CONFIRMATION — sent when an appointment is rescheduled
 * REMINDER_24H          — sent ~24 hours before the appointment start
 * </pre>
 */
public enum NotificationType {
    BOOKING_CONFIRMATION,
    CANCELLATION,
    RESCHEDULE_CONFIRMATION,
    REMINDER_24H
}
