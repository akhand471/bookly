package com.bookly.controller;

import com.bookly.dto.*;
import com.bookly.entity.AppointmentStatus;
import com.bookly.security.CustomUserDetails;
import com.bookly.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * REST endpoints for appointment booking, cancellation, rescheduling, and listing.
 */
@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Appointments", description = "Book, cancel, reschedule, and list appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @Operation(
        summary = "Book a new appointment",
        description = "Books a slot for the authenticated user (customer). " +
                      "Returns HTTP 409 if the slot is already taken or a concurrent booking race occurs."
    )
    public ResponseEntity<ApiResponse<AppointmentResponse>> create(
            @Valid @RequestBody CreateAppointmentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        AppointmentResponse response = appointmentService.create(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Appointment booked successfully"));
    }

    @GetMapping
    @Operation(
        summary = "List appointments",
        description = "Returns a paginated list of appointments for the current business. " +
                      "All filter parameters are optional and can be combined."
    )
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> list(
            @Parameter(description = "Filter by customer UUID")
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Filter by staff UUID")
            @RequestParam(required = false) UUID staffId,
            @Parameter(description = "Filter by status", example = "CONFIRMED")
            @RequestParam(required = false) AppointmentStatus status,
            @Parameter(description = "Filter from this date-time (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Filter to this date-time (inclusive)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by("startTime").descending());
        PageResponse<AppointmentResponse> result =
                appointmentService.list(customerId, staffId, status, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(result, "Appointments retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get appointment by ID", description = "Returns 404 if the appointment is not in the caller's tenant.")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable UUID id) {
        AppointmentResponse response = appointmentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment retrieved successfully"));
    }

    @PostMapping("/{id}/cancel")
    @Operation(
        summary = "Cancel an appointment",
        description = "Sets the appointment status to CANCELLED. " +
                      "Returns HTTP 409 if a concurrent modification occurred (optimistic lock failure)."
    )
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(@PathVariable UUID id) {
        AppointmentResponse response = appointmentService.cancel(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment cancelled successfully"));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(
        summary = "Reschedule an appointment",
        description = "Updates the appointment start/end time in-place. " +
                      "Returns HTTP 409 if the new slot is taken or a concurrent modification occurred."
    )
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable UUID id,
            @Valid @RequestBody RescheduleAppointmentRequest request) {
        AppointmentResponse response = appointmentService.reschedule(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Appointment rescheduled successfully"));
    }
}
