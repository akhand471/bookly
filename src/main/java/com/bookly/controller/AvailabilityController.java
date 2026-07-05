package com.bookly.controller;

import com.bookly.dto.ApiResponse;
import com.bookly.dto.AvailabilityResponse;
import com.bookly.service.AvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Availability query endpoint — returns open time slots for a service + staff + date.
 */
@RestController
@RequestMapping("/api/v1/availability")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Availability", description = "Query available booking slots for a service and staff member")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    @GetMapping
    @Operation(
        summary = "Get available slots",
        description = "Returns open time slots for a specific service + staff combination on a given date. " +
                      "Results account for staff working hours, breaks, schedule overrides, and existing appointments. " +
                      "Results are cached in Redis for 5 minutes."
    )
    public ResponseEntity<ApiResponse<AvailabilityResponse>> getAvailability(
            @Parameter(description = "Service UUID", required = true)
            @RequestParam UUID serviceId,
            @Parameter(description = "Staff member UUID", required = true)
            @RequestParam UUID staffId,
            @Parameter(description = "Date to query (ISO 8601: yyyy-MM-dd)", example = "2025-08-15", required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        AvailabilityResponse response = availabilityService.getAvailability(serviceId, staffId, date);
        return ResponseEntity.ok(ApiResponse.success(response, "Availability retrieved successfully"));
    }
}
