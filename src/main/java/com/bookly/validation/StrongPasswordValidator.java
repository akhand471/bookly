package com.bookly.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validates passwords against a strong policy:
 * - Minimum 8 characters
 * - At least 1 uppercase letter
 * - At least 1 lowercase letter
 * - At least 1 digit
 * - At least 1 special character
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final String UPPER = ".*[A-Z].*";
    private static final String LOWER = ".*[a-z].*";
    private static final String DIGIT = ".*\\d.*";
    private static final String SPECIAL = ".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*";

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.length() < 8) {
            return false;
        }
        return password.matches(UPPER)
                && password.matches(LOWER)
                && password.matches(DIGIT)
                && password.matches(SPECIAL);
    }
}
