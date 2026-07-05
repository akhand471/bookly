package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * Response from the availability engine containing open time slots
 * for a specific service + staff + date combination.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Available booking slots for a service on a specific date")
public class AvailabilityResponse {

    @Schema(description = "ID of the requested service")
    private UUID serviceId;

    @Schema(description = "Name of the service", example = "Haircut")
    private String serviceName;

    @Schema(description = "Duration of the service in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "ID of the staff member")
    private UUID staffId;

    @Schema(description = "The queried date", example = "2025-08-15")
    private LocalDate date;

    @Schema(description = "List of available start times on this date")
    private List<LocalTime> availableSlots;
}
