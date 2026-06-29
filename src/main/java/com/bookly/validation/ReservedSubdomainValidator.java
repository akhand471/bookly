package com.bookly.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

/**
 * Validates that a subdomain is not in the reserved words list.
 * Reserved words are configurable via app.subdomains.reserved property.
 */
public class ReservedSubdomainValidator implements ConstraintValidator<NotReservedSubdomain, String> {

    @Value("${app.subdomains.reserved:www,api,admin,app,mail,ftp,staging,bookly,dashboard,help,support,status,blog}")
    private String reservedWordsConfig;

    private List<String> reservedWords;

    @Override
    public void initialize(NotReservedSubdomain constraintAnnotation) {
        reservedWords = List.of(reservedWordsConfig.split(","));
    }

    @Override
    public boolean isValid(String subdomain, ConstraintValidatorContext context) {
        if (subdomain == null || subdomain.isBlank()) {
            return true; // @NotBlank handles null/blank
        }
        return !reservedWords.contains(subdomain.toLowerCase());
    }
}
