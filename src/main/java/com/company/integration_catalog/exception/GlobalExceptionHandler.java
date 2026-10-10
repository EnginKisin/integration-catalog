package com.company.integration_catalog.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
                .message("Sistemde beklenmeyen bir hata oluştu: " + ex.getMessage())
                .path(request.getRequestURI())
                .build();
                
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}