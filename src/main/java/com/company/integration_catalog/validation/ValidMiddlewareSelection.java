package com.company.integration_catalog.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MiddlewareSelectionValidator.class)
public @interface ValidMiddlewareSelection {
    String message() default "Middleware ID is required when hasMiddleware is true and must be absent otherwise";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
