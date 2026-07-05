package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "Read-only view of a date-specific schedule override")
public class ScheduleOverrideResponse {
    private UUID id;
    private UUID staffId;
    private LocalDate overrideDate;
    @Schema(example = "true")
    private boolean isDayOff;
    private LocalTime startTime;
    private LocalTime endTime;
}
