package com.company.integration_catalog.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Getter
public class ApiException extends ResponseStatusException {
    private final String code;
    private final Map<String, Object> params;
    private final List<Violation> violations;
    private final Long retryAfterSeconds;

    public ApiException(int status, String code, String message) {
        this(status, code, message, Map.of(), List.of(), null);
    }

    public ApiException(int status, String code, String message, Map<String, Object> params,
            List<Violation> violations, Long retryAfterSeconds) {
        super(HttpStatusCode.valueOf(status), message);
        this.code = code;
        this.params = Map.copyOf(params);
        this.violations = List.copyOf(violations);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static ApiException field(int status, String code, String field) {
        return new ApiException(status, code, ErrorCode.message(code), Map.of(),
                List.of(new Violation(field, code, Map.of())), null);
    }

    public int getStatus() { return getStatusCode().value(); }
    @Override public String getMessage() { return getReason(); }
}
