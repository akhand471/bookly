package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only view of a bookable service returned to API callers.
 * Never exposes the JPA entity directly — all fields are mapped via {@link com.bookly.mapper.ServiceMapper}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Details of a bookable service")
public class ServiceResponse {

    @Schema(description = "Unique service identifier")
    private UUID id;

    @Schema(description = "ID of the owning business (tenant)")
    private UUID businessId;

    @Schema(description = "Name of the service", example = "Haircut")
    private String name;

    @Schema(description = "Optional description", example = "Classic scissors cut")
    private String description;

    @Schema(description = "Duration in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "Service price", example = "25.00")
    private BigDecimal price;

    @Schema(description = "Whether the service is currently active", example = "true")
    private boolean isActive;

    @Schema(description = "Creation timestamp")
    private OffsetDateTime createdAt;

    @Schema(description = "Last update timestamp")
    private OffsetDateTime updatedAt;
}
