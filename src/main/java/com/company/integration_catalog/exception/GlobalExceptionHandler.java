package com.company.integration_catalog.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PayloadValidationException.class)
    public ResponseEntity<ErrorResponse> handlePayload(PayloadValidationException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now()).status(ex.getStatus())
                .error(HttpStatus.valueOf(ex.getStatus()).getReasonPhrase())
                .code(ex.getCode()).message(ex.getMessage()).path(request.getRequestURI()).build());
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleLargeUpload(HttpServletRequest request) {
        return handlePayload(new PayloadValidationException(413, "PAYLOAD_TOO_LARGE", "Maximum upload size exceeded"), request);
    }

    @ExceptionHandler({org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleMissingPart(HttpServletRequest request) {
        return handlePayload(new PayloadValidationException("MISSING_UPLOAD_PART", "Metadata and file parts are required"), request);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(ErrorResponse.builder().timestamp(LocalDateTime.now())
                .status(400).error("Bad Request").message("Invalid request body").path(request.getRequestURI()).build());
    }

    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrentChange(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder().timestamp(LocalDateTime.now())
                .status(409).error("Conflict").message("Account changed; reload and try again")
                .path(request.getRequestURI()).build());
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatusCode()).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now()).status(ex.getStatusCode().value())
                .error(HttpStatus.valueOf(ex.getStatusCode().value()).getReasonPhrase())
                .message(ex.getReason()).path(request.getRequestURI()).build());
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataConflict(HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now()).status(409).error("Conflict")
                .message("Data conflicts with an existing record or database constraint")
                .path(request.getRequestURI()).build());
    }

    @ExceptionHandler(IntegrationCompletionException.class)
    public ResponseEntity<ErrorResponse> handleCompletionException(
            IntegrationCompletionException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .error(HttpStatus.CONFLICT.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .details(ex.getDetails())
                .build());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidationException(
            HandlerMethodValidationException ex, HttpServletRequest request) {
        HttpStatus status = ex.isForReturnValue() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(ex.isForReturnValue() ? "Response validation failed" : "Request validation failed")
                .path(request.getRequestURI())
                .build();
        return ResponseEntity.status(status).body(error);
    }

    // 1. Veritabanında kayıt bulunamadığında (404) fırlayacak hata
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFoundException(
            EntityNotFoundException ex, HttpServletRequest request) {

        System.out.println("========== ENTITY NOT FOUND ==========");
        System.out.println("Path    : " + request.getRequestURI());
        System.out.println("Message : " + ex.getMessage());
        ex.printStackTrace();
        System.out.println("======================================");
        
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .build();
                
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex, HttpServletRequest request) {

        System.out.println("========== VALIDATION ERROR ==========");
        System.out.println("Path : " + request.getRequestURI());

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        System.out.println(
                                "Field   : " + error.getField() +
                                " | Message : " + error.getDefaultMessage()
                        )
                );

        System.out.println("======================================");

        String message = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .findFirst()
            .orElse("Validation failed");

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
            .message(message)
            .path(request.getRequestURI())
            .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }


    // 2. Beklenmeyen tüm diğer sistem hataları (500) için güvenli liman
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, HttpServletRequest request) {

        System.out.println("============= GLOBAL ERROR =============");
        System.out.println("Exception : " + ex.getClass().getName());
        System.out.println("Path      : " + request.getRequestURI());
        System.out.println("Message   : " + ex.getMessage());
        System.out.println("=========================================");

        // Hatanın nereden geldiğini görmek için
        ex.printStackTrace();

        System.out.println("=========================================");
        
        ErrorResponse error = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Sistemde beklenmeyen bir hata oluştu")
                .path(request.getRequestURI())
                .build();
                
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
