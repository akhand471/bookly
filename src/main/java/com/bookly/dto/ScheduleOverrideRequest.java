package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for creating or replacing a date-specific schedule override.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Date-specific schedule override (holiday or custom hours)")
public class ScheduleOverrideRequest {

    @NotNull(message = "overrideDate is required")
    @FutureOrPresent(message = "overrideDate must be today or in the future")
    @Schema(description = "The specific date to override", example = "2025-12-25")
    private LocalDate overrideDate;

    @Builder.Default
    @Schema(description = "true = employee is absent that day", example = "true")
    private boolean isDayOff = false;

    @Schema(description = "Custom start time (required when isDayOff=false)", example = "10:00")
    private LocalTime startTime;

    @Schema(description = "Custom end time (required when isDayOff=false)", example = "14:00")
    private LocalTime endTime;
}
