package com.company.integration_catalog.exception;

import java.util.List;

public class IntegrationCompletionException extends RuntimeException {
    private final List<String> details;

    public IntegrationCompletionException(List<String> details) {
        super("Integration is not ready for completion");
        this.details = List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }
}
