package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for setting or replacing a staff member's full weekly schedule.
 * Supplying all 7 days is recommended; missing days are treated as non-working.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Full weekly schedule for a staff member")
public class SetScheduleRequest {

    @NotNull
    @NotEmpty(message = "At least one schedule day is required")
    @Valid
    @Schema(description = "List of schedule entries, one per working/non-working day")
    private List<ScheduleDayRequest> days;
}
