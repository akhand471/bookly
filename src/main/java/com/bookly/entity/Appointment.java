package com.bookly.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Represents a single appointment booking.
 *
 * <h3>Double-Booking Prevention</h3>
 * Two independent guards prevent the same staff member from being double-booked:
 * <ol>
 *   <li><strong>Optimistic locking</strong> — {@code @Version} causes Hibernate to include
 *       {@code WHERE version = ?} on every UPDATE, so concurrent modifications to the same row
 *       throw {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</li>
 *   <li><strong>DB unique constraint</strong> — {@code UNIQUE(staff_id, start_time) WHERE status != 'CANCELLED'}
 *       (partial unique index in V8 migration) is the final safety net for concurrent INSERTs
 *       that race past the application-level slot check.</li>
 * </ol>
 * Both exceptions are translated to HTTP 409 in {@link com.bookly.exception.GlobalExceptionHandler}.
 */
@Entity
@Table(name = "appointments")
@Filter(name = "tenantFilter", condition = "business_id = :businessId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private BookableService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private OffsetDateTime endTime;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "appointment_status", nullable = false)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Optimistic lock version. Hibernate increments this on every UPDATE.
     * A stale-read conflict results in {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.
     */
    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
