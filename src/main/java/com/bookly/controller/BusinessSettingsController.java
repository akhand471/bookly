package com.bookly.controller;

import com.bookly.dto.ApiResponse;
import com.bookly.dto.CancellationPolicyRequest;
import com.bookly.dto.CancellationPolicyResponse;
import com.bookly.service.BusinessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Business-level settings endpoints.
 * Only the business OWNER may read or modify these settings.
 */
@RestController
@RequestMapping("/api/v1/business/settings")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Business Settings", description = "Business-level configuration (cancellation policy, etc.)")
public class BusinessSettingsController {

    private final BusinessService businessService;

    @GetMapping("/cancellation-policy")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(
        summary = "Get cancellation policy",
        description = "Returns the current minimum notice period required for cancellations and reschedules."
    )
    public ResponseEntity<ApiResponse<CancellationPolicyResponse>> getCancellationPolicy() {
        CancellationPolicyResponse policy = businessService.getCancellationPolicy();
        return ResponseEntity.ok(ApiResponse.success(policy, "Cancellation policy retrieved successfully"));
    }

    @PutMapping("/cancellation-policy")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(
        summary = "Update cancellation policy",
        description = "Sets the minimum number of hours before an appointment that a cancellation or reschedule is permitted. " +
                      "Set to 0 to allow cancellations at any time."
    )
    public ResponseEntity<ApiResponse<CancellationPolicyResponse>> updateCancellationPolicy(
            @Valid @RequestBody CancellationPolicyRequest request) {

        CancellationPolicyResponse policy =
                businessService.updateCancellationPolicy(request.getCancellationNoticeHours());
        return ResponseEntity.ok(ApiResponse.success(policy, "Cancellation policy updated successfully"));
    }
}
