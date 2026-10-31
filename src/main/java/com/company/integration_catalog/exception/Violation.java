package com.company.integration_catalog.exception;

import java.util.Map;

public record Violation(String field, String code, Map<String, Object> params) {
    public Violation {
        params = params == null ? Map.of() : Map.copyOf(params);
    }
}
