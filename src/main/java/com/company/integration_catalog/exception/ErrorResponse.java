package com.company.integration_catalog.exception;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ErrorResponse {
    private Instant timestamp;
    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private String requestId;
    @Builder.Default private List<String> details = List.of();
    @Builder.Default private List<Violation> violations = List.of();
    @Builder.Default private Map<String, Object> params = Map.of();
}
