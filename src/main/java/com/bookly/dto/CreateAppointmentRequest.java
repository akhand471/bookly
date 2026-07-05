package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for creating a new appointment booking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to book an appointment")
public class CreateAppointmentRequest {

    @NotNull(message = "serviceId is required")
    @Schema(description = "UUID of the service to book", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID serviceId;

    @NotNull(message = "staffId is required")
    @Schema(description = "UUID of the staff member to book with")
    private UUID staffId;

    @NotNull(message = "startTime is required")
    @Future(message = "startTime must be in the future")
    @Schema(description = "Requested appointment start time (ISO 8601 with offset)", example = "2025-08-15T10:00:00+05:30")
    private OffsetDateTime startTime;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Schema(description = "Optional booking notes", example = "Please use organic products")
    private String notes;
}
