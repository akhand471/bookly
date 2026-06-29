package com.bookly.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "businessId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "business_id = :businessId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    @Size(max = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    @Size(max = 100)
    private String lastName;

    @Column(length = 50)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "business_id")
    private Business business;

    @Builder.Default
    @Column(name = "is_enabled", nullable = false)
    private boolean isEnabled = true;

    @Builder.Default
    @Column(nullable = false, length = 50)
    private String provider = "LOCAL"; // LOCAL, GOOGLE

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
