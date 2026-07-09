package com.bookly.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.passay.CharacterRule;
import org.passay.EnglishCharacterData;
import org.passay.LengthRule;
import org.passay.PasswordData;
import org.passay.PasswordValidator;
import org.passay.RuleResult;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Validates passwords against a strong policy using the Passay library.
 *
 * <p>Rules (identical to the previous regex-based implementation):
 * <ul>
 *   <li>Minimum 8 characters</li>
 *   <li>At least 1 uppercase letter</li>
 *   <li>At least 1 lowercase letter</li>
 *   <li>At least 1 digit</li>
 *   <li>At least 1 special character</li>
 * </ul>
 *
 * <p>On failure, specific Passay messages are surfaced as the constraint violation message
 * instead of the generic annotation-level default. This gives callers actionable feedback
 * (e.g. "Password must contain at least 1 uppercase characters.").
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    private static final PasswordValidator VALIDATOR = new PasswordValidator(List.of(
        new LengthRule(8, Integer.MAX_VALUE),
        new CharacterRule(EnglishCharacterData.UpperCase, 1),
        new CharacterRule(EnglishCharacterData.LowerCase, 1),
        new CharacterRule(EnglishCharacterData.Digit, 1),
        new CharacterRule(EnglishCharacterData.Special, 1)
    ));

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null) return false;

        RuleResult result = VALIDATOR.validate(new PasswordData(password));
        if (result.isValid()) return true;

        // Surface specific per-rule failure messages instead of the generic annotation default
        String details = VALIDATOR.getMessages(result)
                .stream()
                .collect(Collectors.joining(" "));

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(details)
               .addConstraintViolation();

        return false;
    }
}
