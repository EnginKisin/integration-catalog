package com.company.integration_catalog.exception;

import com.company.integration_catalog.security.RequestIdFilter;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return reply(request, ex.getStatus(), ex.getCode(), ex.getParams(), ex.getViolations(), List.of(), ex.getRetryAfterSeconds());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest request) {
        return reply(request, 400, "MISSING_REQUEST_HEADER", Map.of("header", ex.getHeaderName()), List.of(), List.of(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleLargeUpload(HttpServletRequest request) {
        return simple(request, 413, "PAYLOAD_TOO_LARGE");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return reply(request, 400, "MISSING_UPLOAD_PART", Map.of("part", ex.getRequestPartName()), List.of(), List.of(), null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return reply(request, 400, "MISSING_REQUEST_PARAMETER", Map.of("parameter", ex.getParameterName()), List.of(), List.of(), null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return simple(request, 400, "INVALID_REQUEST_BODY");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleInvalidParameter(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return reply(request, 400, "INVALID_PARAMETER", Map.of(),
                List.of(new Violation(ex.getName(), "INVALID_VALUE", Map.of())), List.of(), null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentChange(HttpServletRequest request) {
        return simple(request, 409, "CONCURRENT_MODIFICATION");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        return simple(request, ex.getStatusCode().value(), codeForStatus(ex.getStatusCode().value()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(HttpServletRequest request) {
        return simple(request, 409, "DATA_CONFLICT");
    }

    @ExceptionHandler(IntegrationCompletionException.class)
    public ResponseEntity<ErrorResponse> handleCompletion(IntegrationCompletionException ex, HttpServletRequest request) {
        return reply(request, 409, "INTEGRATION_INCOMPLETE", Map.of(), ex.getViolations(),
                ex.getViolations().stream().map(issue -> ErrorCode.message(issue.code())).toList(), null);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        if (ex.isForReturnValue()) return simple(request, 500, "INTERNAL_ERROR");
        return reply(request, 400, "VALIDATION_FAILED", Map.of(), ValidationErrors.method(ex), List.of(), null);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleValidation(BindException ex, HttpServletRequest request) {
        return reply(request, 400, "VALIDATION_FAILED", Map.of(),
                ValidationErrors.binding(ex.getBindingResult().getAllErrors()), List.of(), null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraints(ConstraintViolationException ex, HttpServletRequest request) {
        var violations = ex.getConstraintViolations().stream().map(issue ->
                ValidationErrors.constraint(issue.getPropertyPath().toString(), issue)).toList();
        return reply(request, 400, "VALIDATION_FAILED", Map.of(), ValidationErrors.sorted(violations), List.of(), null);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex, HttpServletRequest request) {
        String code = ex instanceof ResourceNotFoundException resource ? resource.getCode() : "RESOURCE_NOT_FOUND";
        return simple(request, 404, code);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof org.springframework.web.ErrorResponse framework) {
            var response = simple(request, framework.getStatusCode().value(), codeForStatus(framework.getStatusCode().value()));
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(framework.getHeaders());
            headers.putAll(response.getHeaders());
            return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
        }
        log.error("API request failed requestId={} exceptionType={}", RequestIdFilter.requestId(request), ex.getClass().getName());
        return simple(request, 500, "INTERNAL_ERROR");
    }

    public static String codeForStatus(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "AUTH_REQUIRED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 422 -> "VALIDATION_FAILED";
            case 429 -> "RATE_LIMITED";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }

    private ResponseEntity<ErrorResponse> simple(HttpServletRequest request, int status, String code) {
        return reply(request, status, code, Map.of(), List.of(), List.of(), null);
    }

    private ResponseEntity<ErrorResponse> reply(HttpServletRequest request, int status, String code,
            Map<String, Object> params, List<Violation> violations, List<String> details, Long retryAfter) {
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .header(RequestIdFilter.HEADER, RequestIdFilter.requestId(request)).header("Cache-Control", "no-store");
        if (status == 429) response.header("Retry-After", Long.toString(Math.max(1, retryAfter == null
                ? ("AI_QUEUE_FULL".equals(code) ? 5 : 60) : retryAfter)));
        return response.body(ApiErrors.body(request, status, code, params, violations, details));
    }
}
