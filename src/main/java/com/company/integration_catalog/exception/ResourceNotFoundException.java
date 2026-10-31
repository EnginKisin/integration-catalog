package com.company.integration_catalog.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.Getter;

@Getter
public class ResourceNotFoundException extends EntityNotFoundException {
    private final String code;

    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }
}
