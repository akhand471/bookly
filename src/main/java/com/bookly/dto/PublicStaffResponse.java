package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Public-facing view of a staff member available for booking.
 * Exposes only name and ID — no internal details (role, schedule, etc.).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public view of a staff member available for booking")
public class PublicStaffResponse {

    @Schema(description = "Staff ID (used when submitting a booking)")
    private UUID id;

    @Schema(description = "First name", example = "Jane")
    private String firstName;

    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "Average customer rating score (0.0 to 5.0)", example = "4.8")
    private double averageRating;

    @Schema(description = "Total number of customer reviews", example = "42")
    private long reviewCount;
}
