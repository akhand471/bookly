package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for rescheduling an existing appointment in-place.
 * Optionally changes the staff member as well (e.g. original staff is now unavailable).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to reschedule an existing appointment")
public class RescheduleAppointmentRequest {

    @NotNull(message = "newStartTime is required")
    @Future(message = "newStartTime must be in the future")
    @Schema(description = "New appointment start time (ISO 8601 with offset)", example = "2025-08-16T14:00:00+05:30")
    private OffsetDateTime newStartTime;

    @Schema(description = "Optional: new staff member UUID (leave null to keep the same staff)")
    private UUID newStaffId;
}
