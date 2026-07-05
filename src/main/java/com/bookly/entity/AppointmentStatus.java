package com.bookly.entity;

/**
 * Lifecycle states for an {@link Appointment}.
 *
 * <pre>
 * PENDING  ──► CONFIRMED ──► COMPLETED
 *   │               │
 *   └──► CANCELLED  └──► CANCELLED
 *                   └──► NO_SHOW
 * </pre>
 */
public enum AppointmentStatus {
    /** Booking created but not yet confirmed by the business. */
    PENDING,
    /** Confirmed by the business / owner. */
    CONFIRMED,
    /** Cancelled by customer or staff before the appointment time. */
    CANCELLED,
    /** Service was successfully delivered. */
    COMPLETED,
    /** Customer did not show up. */
    NO_SHOW
}
