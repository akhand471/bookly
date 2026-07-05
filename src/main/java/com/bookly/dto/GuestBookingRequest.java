package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request body for the public guest booking endpoint.
 *
 * <p>No authentication is required. The caller supplies their name, email, and
 * phone number — the backend will either find an existing
 * {@link com.bookly.entity.Customer} record (by email + business) or create one
 * automatically.  This means repeat visitors accumulate booking history without
 * needing an account.</p>
 *
 * <p>{@code staffId} is optional.  When omitted, the service will pick the first
 * available staff member for the requested slot.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Guest booking request — no account required")
public class GuestBookingRequest {

    @NotNull(message = "serviceId is required")
    @Schema(description = "UUID of the service to book", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    private UUID serviceId;

    @Schema(description = "UUID of the preferred staff member (optional — omit for any available)")
    private UUID staffId;

    @NotNull(message = "startTime is required")
    @Future(message = "startTime must be in the future")
    @Schema(description = "Appointment start time (ISO 8601 with offset)", example = "2025-09-01T10:00:00+05:30")
    private OffsetDateTime startTime;

    @NotBlank(message = "customerFirstName is required")
    @Size(max = 100, message = "First name must be at most 100 characters")
    @Schema(description = "Customer's first name", example = "Alex")
    private String customerFirstName;

    @NotBlank(message = "customerLastName is required")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    @Schema(description = "Customer's last name", example = "Smith")
    private String customerLastName;

    @NotBlank(message = "customerEmail is required")
    @Email(message = "customerEmail must be a valid email address")
    @Schema(description = "Customer's email — used to send confirmation and look up booking history", example = "alex@example.com")
    private String customerEmail;

    @Size(max = 50, message = "Phone must be at most 50 characters")
    @Schema(description = "Customer's phone number", example = "+1-555-0100")
    private String customerPhone;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Schema(description = "Optional notes for the booking", example = "Please use organic products")
    private String notes;
}
