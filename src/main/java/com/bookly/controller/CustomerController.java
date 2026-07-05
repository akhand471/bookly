package com.bookly.controller;

import com.bookly.dto.*;
import com.bookly.service.AppointmentService;
import com.bookly.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST endpoints for managing customer profiles and viewing booking history.
 * Accessible to authenticated business owners and staff members.
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Customers", description = "Customer profile management and booking history")
public class CustomerController {

    private final CustomerService customerService;
    private final AppointmentService appointmentService;

    @GetMapping
    @Operation(
        summary = "List all customers",
        description = "Returns a paginated list of all customers for the current business."
    )
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PageResponse<CustomerResponse> result = customerService.listCustomers(pageable);
        return ResponseEntity.ok(ApiResponse.success(result, "Customers retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get customer by ID",
        description = "Returns a single customer profile. Returns 404 if not in the current tenant."
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(
            @PathVariable UUID id) {

        CustomerResponse response = customerService.getCustomerById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Customer retrieved successfully"));
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update customer profile",
        description = "Updates mutable fields (name, phone, notes). Only non-null fields are applied."
    )
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerUpdateRequest request) {

        CustomerResponse response = customerService.updateCustomer(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Customer updated successfully"));
    }

    @GetMapping("/{id}/appointments")
    @Operation(
        summary = "Get customer booking history",
        description = "Returns paginated appointment history for a specific customer."
    )
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> getCustomerAppointments(
            @PathVariable UUID id,
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size (max 100)") @RequestParam(defaultValue = "20") int size) {

        size = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, size, Sort.by("startTime").descending());
        PageResponse<AppointmentResponse> result =
                appointmentService.list(id, null, null, null, null, pageable);
        return ResponseEntity.ok(ApiResponse.success(result, "Booking history retrieved successfully"));
    }
}
