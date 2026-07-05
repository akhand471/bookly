package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Schema(description = "A break window within a staff shift")
public class BreakWindowResponse {
    @Schema(example = "12:00")
    private LocalTime breakStart;
    @Schema(example = "13:00")
    private LocalTime breakEnd;
}
