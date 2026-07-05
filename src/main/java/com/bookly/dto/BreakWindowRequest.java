package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

/**
 * A break window within a shift (e.g. lunch 12:00-13:00).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A break window within a staff shift")
public class BreakWindowRequest {

    @NotNull(message = "breakStart is required")
    @Schema(description = "Break start time", example = "12:00")
    private LocalTime breakStart;

    @NotNull(message = "breakEnd is required")
    @Schema(description = "Break end time (must be after breakStart)", example = "13:00")
    private LocalTime breakEnd;
}
