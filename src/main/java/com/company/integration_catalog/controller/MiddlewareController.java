package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.middleware.MiddlewareRequestDTO;
import com.company.integration_catalog.dto.middleware.MiddlewareResponseDTO;
import com.company.integration_catalog.service.MiddlewareService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/middlewares")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MiddlewareController {

    private final MiddlewareService middlewareService;

    @GetMapping
    public ResponseEntity<List<MiddlewareResponseDTO>> getAllMiddlewares() {
        return ResponseEntity.ok(middlewareService.getAllMiddlewares());
    }

    @PostMapping
    public ResponseEntity<MiddlewareResponseDTO> createMiddleware(@Valid  @RequestBody MiddlewareRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(middlewareService.createMiddleware(request));
    }
}