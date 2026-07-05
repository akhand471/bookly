package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only view of an appointment returned to API callers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full details of an appointment")
public class AppointmentResponse {

    private UUID id;

    @Schema(description = "Owning business (tenant) ID")
    private UUID businessId;

    @Schema(description = "Service booked")
    private UUID serviceId;

    @Schema(description = "Service name", example = "Haircut")
    private String serviceName;

    @Schema(description = "Staff member performing the service")
    private UUID staffId;

    @Schema(description = "Staff member's full name", example = "Jane Doe")
    private String staffName;

    @Schema(description = "Customer who made the booking")
    private UUID customerId;

    @Schema(description = "Customer full name", example = "Alex Smith")
    private String customerName;

    @Schema(description = "Appointment start time (ISO 8601)")
    private OffsetDateTime startTime;

    @Schema(description = "Appointment end time (ISO 8601)")
    private OffsetDateTime endTime;

    @Schema(description = "Current appointment status", example = "CONFIRMED")
    private String status;

    @Schema(description = "Optional booking notes")
    private String notes;

    @Schema(description = "Optimistic lock version (for client-side conflict detection)")
    private long version;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
