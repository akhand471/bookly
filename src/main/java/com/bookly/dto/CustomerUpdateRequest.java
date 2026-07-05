package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating mutable fields of a {@link com.bookly.entity.Customer}.
 * All fields are optional — only non-null values will be applied.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update a customer's profile details")
public class CustomerUpdateRequest {

    @Size(max = 100, message = "First name must be at most 100 characters")
    @Schema(description = "Updated first name", example = "Alexandra")
    private String firstName;

    @Size(max = 100, message = "Last name must be at most 100 characters")
    @Schema(description = "Updated last name", example = "Johnson")
    private String lastName;

    @Size(max = 50, message = "Phone must be at most 50 characters")
    @Schema(description = "Updated phone number", example = "+1-555-0199")
    private String phone;

    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    @Schema(description = "Internal notes about this customer (visible to staff only)")
    private String notes;
}
