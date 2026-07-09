package com.bookly.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StrongPasswordValidator}.
 * Tests every Passay rule individually and edge-cases like null input.
 */
class StrongPasswordValidatorTest {

    private StrongPasswordValidator validator;
    private ConstraintValidatorContext context;
    private ConstraintValidatorContext.ConstraintViolationBuilder builder;

    @BeforeEach
    void setUp() {
        validator = new StrongPasswordValidator();

        // Wire up the mocked context chain for invalid-password tests
        context = mock(ConstraintValidatorContext.class);
        builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    // ── Valid password ─────────────────────────────────────────────────────────

    @Test
    void validPassword_passes() {
        assertThat(validator.isValid("Correct#8", context)).isTrue();
    }

    @Test
    void validPassword_allRequirementsMet_passes() {
        assertThat(validator.isValid("S3cur3P@ss!", context)).isTrue();
    }

    // ── Null / too short ───────────────────────────────────────────────────────

    @Test
    void nullPassword_fails() {
        assertThat(validator.isValid(null, context)).isFalse();
    }

    @Test
    void tooShort_sevenChars_fails() {
        // "Abc1!??" has all character types but only 7 characters
        assertThat(validator.isValid("Abc1!??", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(anyString());
    }

    @Test
    void exactlyEightChars_passes() {
        assertThat(validator.isValid("Abc1!@#$", context)).isTrue();
    }

    // ── Missing character class ────────────────────────────────────────────────

    @Test
    void missingUppercase_fails() {
        assertThat(validator.isValid("lowercase1!", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
    }

    @Test
    void missingLowercase_fails() {
        assertThat(validator.isValid("UPPERCASE1!", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
    }

    @Test
    void missingDigit_fails() {
        assertThat(validator.isValid("NoDigits!", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
    }

    @Test
    void missingSpecialChar_fails() {
        assertThat(validator.isValid("NoSpecial1", context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
    }

    // ── Error message surfacing ────────────────────────────────────────────────

    @Test
    void invalidPassword_surfacesSpecificMessage() {
        validator.isValid("alllower1!", context);

        // A specific Passay message must be set (not the generic annotation default)
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(anyString());
        verify(builder).addConstraintViolation();
    }
}
