package com.bookly.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Read-only view of a {@link com.bookly.entity.Customer} returned to API callers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Customer profile scoped to a single business")
public class CustomerResponse {

    private UUID id;

    @Schema(description = "Owning business (tenant) ID")
    private UUID businessId;

    @Schema(description = "Customer's first name", example = "Alex")
    private String firstName;

    @Schema(description = "Customer's last name", example = "Smith")
    private String lastName;

    @Schema(description = "Customer's email address", example = "alex@example.com")
    private String email;

    @Schema(description = "Customer's phone number", example = "+1-555-0100")
    private String phone;

    @Schema(description = "When the customer record was created")
    private OffsetDateTime createdAt;
}
