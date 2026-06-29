package com.bookly.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = ReservedSubdomainValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotReservedSubdomain {
    String message() default "This subdomain is reserved and cannot be used";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
