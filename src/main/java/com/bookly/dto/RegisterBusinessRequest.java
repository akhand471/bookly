package com.bookly.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.bookly.validation.StrongPassword;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterBusinessRequest {

    @NotBlank(message = "Business name is required")
    @Size(min = 2, max = 100, message = "Business name must be between 2 and 100 characters")
    private String businessName;

    @NotBlank(message = "Subdomain is required")
    @Size(min = 2, max = 50, message = "Subdomain must be between 2 and 50 characters")
    private String subdomain;

    @NotBlank(message = "Owner first name is required")
    @Size(max = 50, message = "First name must not exceed 50 characters")
    private String ownerFirstName;

    @NotBlank(message = "Owner last name is required")
    @Size(max = 50, message = "Last name must not exceed 50 characters")
    private String ownerLastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    private String email;

    @NotBlank(message = "Password is required")
    @StrongPassword
    @Size(max = 50, message = "Password must not exceed 50 characters")
    private String password;
}
