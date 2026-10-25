package com.company.integration_catalog.exception;

import lombok.Getter;

@Getter
public class PayloadValidationException extends RuntimeException {
    private final String code;
    private final int status;

    public PayloadValidationException(String code, String message) {
        this(400, code, message);
    }

    public PayloadValidationException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}
