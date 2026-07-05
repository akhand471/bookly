package com.bookly.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A date-specific schedule override for a staff member.
 * Takes precedence over the recurring {@link StaffSchedule} for the same date.
 *
 * <ul>
 *   <li>{@code isDayOff = true} — the employee is absent; no appointments should be offered.</li>
 *   <li>{@code isDayOff = false} — the employee works custom hours on this date
 *       ({@code startTime} and {@code endTime} must be non-null).</li>
 * </ul>
 */
@Entity
@Table(name = "staff_schedule_overrides")
@Filter(name = "tenantFilter", condition = "business_id = :businessId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffScheduleOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(name = "override_date", nullable = false)
    private LocalDate overrideDate;

    @Builder.Default
    @Column(name = "is_day_off", nullable = false)
    private boolean isDayOff = false;

    /** Non-null when {@code isDayOff = false}. */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** Non-null when {@code isDayOff = false}. */
    @Column(name = "end_time")
    private LocalTime endTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
