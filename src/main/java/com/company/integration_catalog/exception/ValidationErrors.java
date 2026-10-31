package com.company.integration_catalog.exception;

import jakarta.validation.ConstraintViolation;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ValidationErrors {
    private ValidationErrors() {}

    public static List<Violation> binding(List<ObjectError> errors) {
        return sorted(errors.stream().map(error -> {
            String field = error instanceof FieldError item ? item.getField() : null;
            try { return constraint(field, error.unwrap(ConstraintViolation.class)); }
            catch (IllegalArgumentException ex) { return new Violation(field, "INVALID_VALUE", Map.of()); }
        }).toList());
    }

    public static List<Violation> method(HandlerMethodValidationException exception) {
        List<Violation> violations = new ArrayList<>();
        for (var result : exception.getParameterValidationResults()) {
            String prefix = result.getContainerIndex() == null ? "" : "[" + result.getContainerIndex() + "]";
            if (result instanceof ParameterErrors errors) {
                for (Violation violation : binding(errors.getAllErrors())) {
                    String field = violation.field();
                    if (!prefix.isEmpty()) field = field == null ? prefix : prefix + "." + field;
                    violations.add(new Violation(field, violation.code(), violation.params()));
                }
            } else {
                String field = result.getMethodParameter().hasParameterAnnotation(RequestBody.class)
                        ? (prefix.isEmpty() ? null : prefix) : result.getMethodParameter().getParameterName();
                for (var error : result.getResolvableErrors()) {
                    try { violations.add(constraint(field, result.unwrap(error, ConstraintViolation.class))); }
                    catch (IllegalArgumentException ex) { violations.add(new Violation(field, "INVALID_VALUE", Map.of())); }
                }
            }
        }
        return sorted(violations);
    }

    public static Violation constraint(String field, ConstraintViolation<?> violation) {
        var descriptor = violation.getConstraintDescriptor();
        String type = descriptor.getAnnotation().annotationType().getSimpleName();
        Map<String, Object> attributes = descriptor.getAttributes();
        String code = switch (type) {
            case "NotNull", "NotBlank", "NotEmpty" -> "REQUIRED";
            case "Email" -> "INVALID_EMAIL";
            case "Size" -> "INVALID_LENGTH";
            case "Pattern" -> "INVALID_FORMAT";
            case "Min", "DecimalMin" -> "MIN_VALUE";
            case "Max", "DecimalMax" -> "MAX_VALUE";
            case "Positive" -> "POSITIVE_REQUIRED";
            case "PositiveOrZero" -> "NON_NEGATIVE_REQUIRED";
            case "ValidMiddlewareSelection" -> "INVALID_MIDDLEWARE_SELECTION";
            default -> "INVALID_VALUE";
        };
        Map<String, Object> params = switch (type) {
            case "Size" -> Map.of("min", attributes.get("min"), "max", attributes.get("max"));
            case "Min", "DecimalMin" -> Map.of("min", attributes.get("value"));
            case "Max", "DecimalMax" -> Map.of("max", attributes.get("value"));
            default -> Map.of();
        };
        return new Violation(field, code, params);
    }

    public static List<Violation> sorted(List<Violation> violations) {
        return violations.stream().distinct().sorted(Comparator.comparing(
                Violation::field, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(Violation::code)).toList();
    }
}
