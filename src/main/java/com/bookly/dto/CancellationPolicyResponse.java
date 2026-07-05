package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Read-only view of a business's cancellation policy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Business cancellation policy settings")
public class CancellationPolicyResponse {

    @Schema(
        description = "Minimum hours before appointment start that a cancellation or reschedule is allowed. 0 = no restriction.",
        example = "2"
    )
    private int cancellationNoticeHours;
}
