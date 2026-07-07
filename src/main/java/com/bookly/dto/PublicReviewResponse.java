package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer review response for public listing pages")
public class PublicReviewResponse {

    @Schema(description = "Masked or formatted customer name", example = "Alex S.")
    private String customerName;

    @Schema(description = "Rating score (1 to 5)", example = "5")
    private int rating;

    @Schema(description = "Customer comment text", example = "Perfect cut!")
    private String comment;

    @Schema(description = "Review creation timestamp")
    private OffsetDateTime createdAt;
}
