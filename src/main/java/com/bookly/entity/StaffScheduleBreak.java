package com.bookly.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

/**
 * A break window within a {@link StaffSchedule}.
 * Multiple breaks can exist per schedule row (e.g. lunch 12-13:00, tea break 15:30-15:45).
 */
@Entity
@Table(name = "staff_schedule_breaks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffScheduleBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private StaffSchedule schedule;

    @Column(name = "break_start", nullable = false)
    private LocalTime breakStart;

    @Column(name = "break_end", nullable = false)
    private LocalTime breakEnd;
}
