package com.company.integration_catalog.validation;

import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class MiddlewareSelectionValidator
        implements ConstraintValidator<ValidMiddlewareSelection, IntegrationRequestDTO> {

    @Override
    public boolean isValid(IntegrationRequestDTO request, ConstraintValidatorContext context) {
        // Missing hasMiddleware is reported by its own @NotNull constraint.
        if (request == null || request.getHasMiddleware() == null) {
            return true;
        }
        boolean valid = request.getHasMiddleware()
                ? request.getMiddlewareId() != null
                : request.getMiddlewareId() == null;
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("middlewareId")
                    .addConstraintViolation();
        }
        return valid;
    }
}
