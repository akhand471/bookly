package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Read-only view of a staff member's schedule for one weekday")
public class ScheduleDayResponse {
    @Schema(description = "ISO day of week: 1=MONDAY, 7=SUNDAY", example = "1")
    private int dayOfWeek;
    @Schema(example = "true")
    private boolean isWorkingDay;
    private LocalTime startTime;
    private LocalTime endTime;
    @Schema(description = "Break windows within the shift")
    private List<BreakWindowResponse> breaks;
}
