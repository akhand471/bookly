package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed review response for business administration")
public class ReviewResponse {

    @Schema(description = "Unique review identifier")
    private UUID id;

    @Schema(description = "Associated appointment identifier")
    private UUID appointmentId;

    @Schema(description = "Associated service identifier")
    private UUID serviceId;

    @Schema(description = "Name of the service")
    private String serviceName;

    @Schema(description = "Associated staff member identifier")
    private UUID staffId;

    @Schema(description = "Full name of the staff member")
    private String staffName;

    @Schema(description = "Full name of the customer")
    private String customerName;

    @Schema(description = "Rating score (1 to 5)")
    private int rating;

    @Schema(description = "Review comments text")
    private String comment;

    @Schema(description = "Creation timestamp")
    private OffsetDateTime createdAt;
}
