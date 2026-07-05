package com.bookly.controller;

import com.bookly.dto.*;
import com.bookly.service.AvailabilityService;
import com.bookly.service.PublicBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public (unauthenticated) customer-facing booking endpoints.
 *
 * <p>All endpoints are scoped to a specific business via the {@code {subdomain}} path variable —
 * e.g. {@code GET /api/v1/public/barber/services} lists services for the barber shop.</p>
 *
 * <p>No JWT is required.  The {@code SecurityConfig} explicitly permits {@code /api/v1/public/**}.</p>
 */
@RestController
@RequestMapping("/api/v1/public/{subdomain}")
@RequiredArgsConstructor
@Tag(name = "Public Booking", description = "Unauthenticated endpoints for customer-facing booking flow")
public class PublicBookingController {

    private final PublicBookingService publicBookingService;

    // ─── Browse ────────────────────────────────────────────────────────────

    @GetMapping("/services")
    @Operation(
        summary = "List services for a business",
        description = "Returns all active services offered by the business. No authentication required."
    )
    public ResponseEntity<ApiResponse<List<PublicServiceResponse>>> getServices(
            @Parameter(description = "Business subdomain slug", example = "barber")
            @PathVariable String subdomain) {

        List<PublicServiceResponse> services = publicBookingService.getPublicServices(subdomain);
        return ResponseEntity.ok(ApiResponse.success(services, "Services retrieved successfully"));
    }

    @GetMapping("/staff")
    @Operation(
        summary = "List staff members for a business",
        description = "Returns all active staff available for booking. No authentication required."
    )
    public ResponseEntity<ApiResponse<List<PublicStaffResponse>>> getStaff(
            @PathVariable String subdomain) {

        List<PublicStaffResponse> staff = publicBookingService.getPublicStaff(subdomain);
        return ResponseEntity.ok(ApiResponse.success(staff, "Staff retrieved successfully"));
    }

    @GetMapping("/availability")
    @Operation(
        summary = "Get available booking slots",
        description = "Returns available time slots for a specific service, staff, and date. No authentication required."
    )
    public ResponseEntity<ApiResponse<AvailabilityResponse>> getAvailability(
            @PathVariable String subdomain,
            @Parameter(description = "Service UUID") @RequestParam UUID serviceId,
            @Parameter(description = "Staff UUID") @RequestParam UUID staffId,
            @Parameter(description = "Date in ISO format", example = "2025-09-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        AvailabilityResponse availability =
                publicBookingService.getPublicAvailability(subdomain, serviceId, staffId, date);
        return ResponseEntity.ok(ApiResponse.success(availability, "Availability retrieved successfully"));
    }

    // ─── Book ──────────────────────────────────────────────────────────────

    @PostMapping("/bookings")
    @Operation(
        summary = "Create a guest booking",
        description = "Books an appointment without requiring an account. " +
                      "A customer profile is created automatically (or looked up by email for repeat visitors). " +
                      "A confirmation email is sent to the provided address. " +
                      "Returns HTTP 409 if the slot is already taken."
    )
    public ResponseEntity<ApiResponse<PublicBookingResponse>> createBooking(
            @PathVariable String subdomain,
            @Valid @RequestBody GuestBookingRequest request) {

        PublicBookingResponse response = publicBookingService.createGuestBooking(subdomain, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Booking created successfully. A confirmation email has been sent."));
    }

    @GetMapping("/bookings/{id}")
    @Operation(
        summary = "Get booking details",
        description = "Returns the status and details of a booking by its ID. No authentication required — the booking ID is the access token."
    )
    public ResponseEntity<ApiResponse<PublicBookingResponse>> getBooking(
            @PathVariable String subdomain,
            @PathVariable UUID id) {

        PublicBookingResponse response = publicBookingService.getPublicBooking(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Booking retrieved successfully"));
    }

    @PostMapping("/bookings/{id}/cancel")
    @Operation(
        summary = "Cancel a booking (customer)",
        description = "Allows a customer to cancel their own booking by providing the booking ID and their email address. " +
                      "The cancellation policy (minimum notice period) is enforced. " +
                      "Returns HTTP 400 if the policy window has passed."
    )
    public ResponseEntity<ApiResponse<PublicBookingResponse>> cancelBooking(
            @PathVariable String subdomain,
            @PathVariable UUID id,
            @Parameter(description = "Customer email — used to verify ownership of the booking")
            @RequestParam String customerEmail) {

        PublicBookingResponse response = publicBookingService.cancelPublicBooking(id, customerEmail);
        return ResponseEntity.ok(ApiResponse.success(response, "Booking cancelled successfully"));
    }
}
