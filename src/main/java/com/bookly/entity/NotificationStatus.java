package com.bookly.entity;

/**
 * Delivery status of a notification attempt.
 *
 * <ul>
 *   <li>{@code SENT}   — email was accepted by the mail server</li>
 *   <li>{@code FAILED} — delivery attempt failed (see {@link NotificationLog#errorMessage})</li>
 * </ul>
 */
public enum NotificationStatus {
    SENT,
    FAILED
}
