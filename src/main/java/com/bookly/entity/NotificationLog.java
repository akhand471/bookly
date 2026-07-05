package com.bookly.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Immutable audit record of every notification attempt.
 *
 * <p>A unique partial index on {@code (appointment_id, type) WHERE status = 'SENT'}
 * (defined in V12 migration) guarantees that at most one SENT entry exists per
 * (appointment, notification type) combination — preventing duplicate sends even
 * under concurrent retries.</p>
 *
 * <p>{@code FAILED} entries are allowed to accumulate to support diagnostics and retry.</p>
 */
@Entity
@Table(name = "notification_log")
@Filter(name = "tenantFilter", condition = "business_id = :businessId")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "notification_type", nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "notification_status", nullable = false)
    private NotificationStatus status;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
