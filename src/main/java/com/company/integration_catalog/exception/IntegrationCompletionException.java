package com.company.integration_catalog.exception;

import java.util.List;
import java.util.Map;

public class IntegrationCompletionException extends RuntimeException {
    private final List<String> details;
    private final List<Violation> violations;

    public IntegrationCompletionException(List<String> details) {
        this(details, details.stream().map(value -> new Violation(null, "INTEGRATION_INCOMPLETE", Map.of())).toList());
    }

    public IntegrationCompletionException(List<String> details, List<Violation> violations) {
        super("Integration is not ready for completion");
        this.details = List.copyOf(details);
        this.violations = List.copyOf(violations);
    }

    public List<String> getDetails() { return details; }
    public List<Violation> getViolations() { return violations; }
}
