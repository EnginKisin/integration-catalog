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
public class MiddlewareController {

    private final MiddlewareService middlewareService;

    @GetMapping("/{id}")
    public ResponseEntity<MiddlewareResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(middlewareService.getMiddlewareById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MiddlewareResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody MiddlewareRequestDTO request) {
        return ResponseEntity.ok(middlewareService.updateMiddleware(id, request));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<MiddlewareResponseDTO> setActive(@PathVariable Long id,
            @Valid @RequestBody com.company.integration_catalog.dto.ActiveStatusRequest request) {
        return ResponseEntity.ok(middlewareService.setMiddlewareActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        middlewareService.deleteMiddleware(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<MiddlewareResponseDTO>> getAllMiddlewares(
            @RequestParam(required = false) Boolean active) {
        List<MiddlewareResponseDTO> items = middlewareService.getAllMiddlewares();
        return ResponseEntity.ok(active == null ? items
                : items.stream().filter(item -> item.isActive() == active).toList());
    }

    @PostMapping
    public ResponseEntity<MiddlewareResponseDTO> createMiddleware(@Valid  @RequestBody MiddlewareRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(middlewareService.createMiddleware(request));
    }
}
