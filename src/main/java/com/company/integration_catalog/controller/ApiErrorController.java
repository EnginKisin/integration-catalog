package com.company.integration_catalog.controller;

import com.company.integration_catalog.exception.ApiErrors;
import com.company.integration_catalog.exception.ErrorResponse;
import com.company.integration_catalog.exception.GlobalExceptionHandler;
import com.company.integration_catalog.security.RequestIdFilter;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;

@RestController
public class ApiErrorController implements ErrorController {
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> error(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = value instanceof Integer number && number >= 400 && number <= 599 ? number : 500;
        var response = ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .header(RequestIdFilter.HEADER, RequestIdFilter.requestId(request)).header("Cache-Control", "no-store");
        if (status == 429) response.header("Retry-After", "60");
        return response.body(ApiErrors.body(request, status, GlobalExceptionHandler.codeForStatus(status),
                Map.of(), List.of(), List.of()));
    }
}
