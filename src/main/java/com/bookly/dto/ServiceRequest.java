package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for creating or updating a bookable service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Payload to create or update a bookable service")
public class ServiceRequest {

    @NotBlank(message = "Service name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    @Schema(description = "Name of the service", example = "Haircut")
    private String name;

    @Size(max = 2000, message = "Description must be at most 2000 characters")
    @Schema(description = "Optional description of the service", example = "Classic scissors cut with wash and blow-dry")
    private String description;

    @NotNull(message = "Duration is required")
    @Min(value = 1, message = "Duration must be at least 1 minute")
    @Schema(description = "Duration of the service in minutes", example = "30")
    private Integer durationMinutes;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price must be zero or greater")
    @Schema(description = "Service price (supports decimals)", example = "25.00")
    private BigDecimal price;

    @Builder.Default
    @Schema(description = "Whether this service is currently offered", example = "true")
    private boolean isActive = true;
}
