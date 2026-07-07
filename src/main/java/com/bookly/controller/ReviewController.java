package com.bookly.controller;

import com.bookly.dto.ApiResponse;
import com.bookly.dto.PageResponse;
import com.bookly.dto.ReviewResponse;
import com.bookly.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews Administration", description = "Endpoints for staff/owners to view and moderate customer reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping
    @PreAuthorize("hasAnyRole('BUSINESS_OWNER', 'EMPLOYEE')")
    @Operation(
        summary = "List all reviews for the business",
        description = "Returns a paginated list of reviews for the active tenant business. Requires BUSINESS_OWNER or EMPLOYEE authentication."
    )
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> listReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        PageResponse<ReviewResponse> response = reviewService.getBusinessReviews(pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Reviews retrieved successfully"));
    }
}
