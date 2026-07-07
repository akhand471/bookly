package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Public-facing view of a bookable service.
 * Exposes only the fields a customer needs to make a booking decision.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public view of a bookable service")
public class PublicServiceResponse {

    @Schema(description = "Service ID (used when submitting a booking)")
    private UUID id;

    @Schema(description = "Service name", example = "Classic Haircut")
    private String name;

    @Schema(description = "Short description of the service", example = "A clean, classic cut with scissors and comb")
    private String description;

    @Schema(description = "Duration of the service in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "Price of the service", example = "25.00")
    private BigDecimal price;

    @Schema(description = "Average customer rating score (0.0 to 5.0)", example = "4.8")
    private double averageRating;

    @Schema(description = "Total number of customer reviews", example = "42")
    private long reviewCount;
}
