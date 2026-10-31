package com.company.integration_catalog.exception;

import java.util.List;
import java.util.Map;

public class PayloadValidationException extends ApiException {
    public PayloadValidationException(String code, String message) {
        this(400, code, message);
    }

    public PayloadValidationException(int status, String code, String message) {
        super(status, code, message);
    }

    public PayloadValidationException(int status, String code, String message, Map<String, Object> params) {
        super(status, code, message, params, List.of(), null);
    }
}
