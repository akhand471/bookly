package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating the business's cancellation policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update the business cancellation policy")
public class CancellationPolicyRequest {

    @NotNull(message = "cancellationNoticeHours is required")
    @Min(value = 0, message = "cancellationNoticeHours must be 0 or greater")
    @Max(value = 720, message = "cancellationNoticeHours must not exceed 720 (30 days)")
    @Schema(
        description = "Minimum hours before appointment start that cancellation is permitted. 0 = no restriction.",
        example = "24"
    )
    private Integer cancellationNoticeHours;
}
