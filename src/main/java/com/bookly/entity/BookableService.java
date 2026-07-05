package com.bookly.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A bookable service offered by a business (e.g. "Haircut 30 min").
 * Tenant-isolated: the Hibernate tenantFilter restricts all queries to the
 * current business so cross-tenant leakage is impossible at the ORM layer.
 * Deleted services are soft-deleted via {@code isActive = false}.
 */
@Entity
@Table(name = "services")
@Filter(name = "tenantFilter", condition = "business_id = :businessId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookableService {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id", nullable = false)
    private Business business;

    @Column(nullable = false, length = 150)
    @Size(max = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Duration of the service in minutes. Must be positive. */
    @Column(name = "duration_minutes", nullable = false)
    @Min(1)
    private int durationMinutes;

    /** Price in the business's local currency. Stored as NUMERIC(10,2). */
    @Column(nullable = false, precision = 10, scale = 2)
    @DecimalMin("0.00")
    private BigDecimal price;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
