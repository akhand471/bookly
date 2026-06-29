package com.bookly.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "businesses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    @Size(max = 100)
    private String name;

    @Column(unique = true, length = 50)
    @Size(max = 50)
    private String subdomain;

    @Column(name = "logo_url", length = 512)
    private String logoUrl;

    @Column(length = 50)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Builder.Default
    @Column(nullable = false)
    private String timezone = "UTC";

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
