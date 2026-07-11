package com.bookly.controller;

import com.bookly.dto.*;
import com.bookly.service.StaffScheduleService;
import com.bookly.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for managing staff working hours and schedule overrides.
 * <p>
 * BUSINESS_OWNER can manage any staff member's schedule.
 * EMPLOYEE can read their own schedule.
 */
import com.bookly.service.UserService;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Staff Schedules", description = "Manage staff working hours, breaks, and date-specific overrides")
public class StaffScheduleController {

    private final StaffScheduleService staffScheduleService;
    private final UserService userService;

    // ─── Weekly Schedule ───────────────────────────────────────────────────

    @PutMapping("/{staffId}/schedule")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(
        summary = "Set staff weekly schedule",
        description = "Replaces the full recurring weekly schedule for a staff member. " +
                      "This operation is idempotent — existing rows are deleted and replaced. " +
                      "Restricted to BUSINESS_OWNER."
    )
    public ResponseEntity<ApiResponse<List<ScheduleDayResponse>>> setSchedule(
            @PathVariable UUID staffId,
            @Valid @RequestBody SetScheduleRequest request) {
        List<ScheduleDayResponse> result = staffScheduleService.setSchedule(staffId, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Schedule updated successfully"));
    }

    @GetMapping("/{staffId}/schedule")
    @Operation(
        summary = "Get staff weekly schedule",
        description = "Returns the recurring weekly schedule for the specified staff member. " +
                      "Accessible by BUSINESS_OWNER and the staff member themselves."
    )
    public ResponseEntity<ApiResponse<List<ScheduleDayResponse>>> getSchedule(
            @PathVariable UUID staffId) {
        List<ScheduleDayResponse> result = staffScheduleService.getSchedule(staffId);
        return ResponseEntity.ok(ApiResponse.success(result, "Schedule retrieved successfully"));
    }

    // ─── Date Overrides ────────────────────────────────────────────────────

    @PostMapping("/{staffId}/schedule/overrides")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(
        summary = "Create or replace a date-specific schedule override",
        description = "Use isDayOff=true for holidays/absences. " +
                      "Use isDayOff=false with startTime/endTime for custom hours on a specific date."
    )
    public ResponseEntity<ApiResponse<ScheduleOverrideResponse>> setOverride(
            @PathVariable UUID staffId,
            @Valid @RequestBody ScheduleOverrideRequest request) {
        ScheduleOverrideResponse result = staffScheduleService.setOverride(staffId, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Schedule override set successfully"));
    }

    @GetMapping("/{staffId}/schedule/overrides")
    @Operation(
        summary = "List schedule overrides for a staff member",
        description = "Returns all date-specific overrides within the given date range."
    )
    public ResponseEntity<ApiResponse<List<ScheduleOverrideResponse>>> listOverrides(
            @PathVariable UUID staffId,
            @Parameter(description = "Start of date range (inclusive)", example = "2025-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End of date range (inclusive)", example = "2025-01-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<ScheduleOverrideResponse> result = staffScheduleService.listOverrides(staffId, from, to);
        return ResponseEntity.ok(ApiResponse.success(result, "Overrides retrieved successfully"));
    }

    @DeleteMapping("/{staffId}/schedule/overrides/{date}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(
        summary = "Delete a date-specific override",
        description = "Removes the override for the given date, restoring the recurring schedule for that day."
    )
    public ResponseEntity<ApiResponse<Void>> deleteOverride(
            @PathVariable UUID staffId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        staffScheduleService.deleteOverride(staffId, date);
        return ResponseEntity.ok(ApiResponse.success(null, "Schedule override deleted successfully"));
    }

    @GetMapping
    @Operation(
        summary = "List all staff members for the business",
        description = "Returns all active staff/employees registered under the business. Accessible by BUSINESS_OWNER and EMPLOYEES."
    )
    public ResponseEntity<ApiResponse<List<UserResponse>>> getStaff(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<UserResponse> result = staffScheduleService.getStaff(userDetails.getBusinessId());
        return ResponseEntity.ok(ApiResponse.success(result, "Staff list retrieved successfully"));
    }

    @GetMapping("/{staffId}")
    @Operation(
        summary = "Get a staff member's profile by ID",
        description = "Returns a staff member's profile details. Accessible by BUSINESS_OWNER, EMPLOYEES and CUSTOMERS."
    )
    public ResponseEntity<ApiResponse<UserResponse>> getStaffMember(
            @PathVariable UUID staffId) {
        UserResponse response = userService.getUserById(staffId);
        return ResponseEntity.ok(ApiResponse.success(response, "Staff profile retrieved successfully"));
    }
}
