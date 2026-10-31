package com.company.integration_catalog.exception;

import com.company.integration_catalog.security.RequestIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class ApiErrors {
    private ApiErrors() {}

    public static ErrorResponse body(HttpServletRequest request, int status, String code,
            Map<String, Object> params, List<Violation> violations, List<String> details) {
        HttpStatus httpStatus = HttpStatus.resolve(status);
        org.slf4j.LoggerFactory.getLogger(ApiErrors.class).warn("API error requestId={} status={} code={}",
                RequestIdFilter.requestId(request), status, code);
        return ErrorResponse.builder().timestamp(Instant.now()).status(status)
                .error(httpStatus == null ? "HTTP Error" : httpStatus.getReasonPhrase())
                .code(code).message(ErrorCode.message(code)).path(
                        request.getAttribute(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI) instanceof String original
                                ? original : request.getRequestURI())
                .requestId(RequestIdFilter.requestId(request)).params(params)
                .violations(violations).details(details).build();
    }

    public static void write(ObjectMapper mapper, HttpServletRequest request, HttpServletResponse response,
            int status, String code, Long retryAfterSeconds) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(RequestIdFilter.HEADER, RequestIdFilter.requestId(request));
        if (status == 429) response.setHeader("Retry-After", Long.toString(Math.max(1, retryAfterSeconds == null ? 60 : retryAfterSeconds)));
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), body(request, status, code, Map.of(), List.of(), List.of()));
    }
}
