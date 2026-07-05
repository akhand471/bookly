package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response returned to a guest after booking, cancelling, or viewing a booking.
 * Contains enough information to render a confirmation or status page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Booking confirmation details shown to the customer after booking")
public class PublicBookingResponse {

    @Schema(description = "Booking ID — save this to cancel or view the booking later")
    private UUID bookingId;

    @Schema(description = "Business name", example = "Alex's Barber Shop")
    private String businessName;

    @Schema(description = "Name of the booked service", example = "Classic Haircut")
    private String serviceName;

    @Schema(description = "Duration of the service in minutes", example = "30")
    private int durationMinutes;

    @Schema(description = "Name of the assigned staff member", example = "Jane Doe")
    private String staffName;

    @Schema(description = "Appointment start time (ISO 8601)", example = "2025-09-01T10:00:00+05:30")
    private OffsetDateTime startTime;

    @Schema(description = "Appointment end time (ISO 8601)", example = "2025-09-01T10:30:00+05:30")
    private OffsetDateTime endTime;

    @Schema(description = "Current booking status", example = "PENDING")
    private String status;

    @Schema(description = "Email address where the confirmation was sent", example = "alex@example.com")
    private String customerEmail;
}
