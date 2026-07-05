package com.bookly.controller;

import com.bookly.dto.ApiResponse;
import com.bookly.dto.PageResponse;
import com.bookly.dto.ServiceRequest;
import com.bookly.dto.ServiceResponse;
import com.bookly.service.BookableServiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for bookable service management.
 * <p>
 * All endpoints require authentication (Bearer JWT).
 * Write operations (create / update / delete) are restricted to BUSINESS_OWNER.
 * Read operations (list / get) are accessible to any authenticated user of the tenant.
 */
@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Services", description = "Manage bookable services offered by the business")
public class ServiceController {

    private final BookableServiceService bookableServiceService;

    @PostMapping
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(summary = "Create a new bookable service",
               description = "Creates a service scoped to the authenticated owner's business. " +
                             "Duplicate names (case-insensitive) within the same business are rejected.")
    public ResponseEntity<ApiResponse<ServiceResponse>> create(
            @Valid @RequestBody ServiceRequest request) {
        ServiceResponse response = bookableServiceService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Service created successfully"));
    }

    @GetMapping
    @Operation(summary = "List active services for the current business",
               description = "Returns a paginated list of active (non-deleted) services scoped to the tenant.")
    public ResponseEntity<ApiResponse<PageResponse<ServiceResponse>>> list(
            @Parameter(description = "Zero-based page index", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Number of items per page (max 100)", example = "20")
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100); // guard against runaway page sizes
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        PageResponse<ServiceResponse> result = bookableServiceService.listActive(pageable);
        return ResponseEntity.ok(ApiResponse.success(result, "Services retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single service by ID",
               description = "Returns service details. Returns 404 if the service does not belong to the caller's business.")
    public ResponseEntity<ApiResponse<ServiceResponse>> getById(
            @PathVariable UUID id) {
        ServiceResponse response = bookableServiceService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Service retrieved successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(summary = "Update a service",
               description = "Fully replaces the service's fields. Restricted to BUSINESS_OWNER.")
    public ResponseEntity<ApiResponse<ServiceResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceRequest request) {
        ServiceResponse response = bookableServiceService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Service updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('BUSINESS_OWNER')")
    @Operation(summary = "Soft-delete a service",
               description = "Marks the service as inactive (isActive=false). " +
                             "The record is retained for historical appointment references.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        bookableServiceService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Service deleted successfully"));
    }
}
