package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.system.SystemRequestDTO;
import com.company.integration_catalog.dto.system.SystemResponseDTO;
import com.company.integration_catalog.service.SystemService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/systems")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/{id}")
    public ResponseEntity<SystemResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(systemService.getSystemById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SystemResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody SystemRequestDTO request) {
        return ResponseEntity.ok(systemService.updateSystem(id, request));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<SystemResponseDTO> setActive(@PathVariable Long id,
            @Valid @RequestBody com.company.integration_catalog.dto.ActiveStatusRequest request) {
        return ResponseEntity.ok(systemService.setSystemActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        systemService.deleteSystem(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<SystemResponseDTO>> getAllSystems(
            @RequestParam(required = false) Boolean active) {
        List<SystemResponseDTO> items = systemService.getAllSystems();
        return ResponseEntity.ok(active == null ? items
                : items.stream().filter(item -> item.isActive() == active).toList());
    }

    @PostMapping
    public ResponseEntity<SystemResponseDTO> createSystem(@Valid @RequestBody SystemRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(systemService.createSystem(request));
    }
}
