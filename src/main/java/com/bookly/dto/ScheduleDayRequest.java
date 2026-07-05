package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One day-of-week entry within a staff schedule set request.
 * DayOfWeek is an integer following ISO-8601: 1=MONDAY … 7=SUNDAY.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Schedule entry for one day of the week")
public class ScheduleDayRequest {

    @NotNull(message = "dayOfWeek is required (1=MONDAY … 7=SUNDAY)")
    @Schema(description = "ISO day of week: 1=MONDAY, 7=SUNDAY", example = "1")
    private Integer dayOfWeek;

    @Schema(description = "Whether the employee works on this day", example = "true")
    @Builder.Default
    private boolean isWorkingDay = true;

    @Schema(description = "Shift start time (required when isWorkingDay=true)", example = "09:00")
    private LocalTime startTime;

    @Schema(description = "Shift end time (required when isWorkingDay=true)", example = "17:00")
    private LocalTime endTime;

    @Valid
    @Builder.Default
    @Schema(description = "Optional break windows within the shift")
    private List<BreakWindowRequest> breaks = new ArrayList<>();
}
