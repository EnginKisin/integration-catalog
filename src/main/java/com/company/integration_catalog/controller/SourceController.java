package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.source.SourceRequestDTO;
import com.company.integration_catalog.dto.source.SourceResponseDTO;
import com.company.integration_catalog.service.SourceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SourceController {

    private final SourceService sourceService;

    @GetMapping("/{id}")
    public ResponseEntity<SourceResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(sourceService.getSourceById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<SourceResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody SourceRequestDTO request) {
        return ResponseEntity.ok(sourceService.updateSource(id, request));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<SourceResponseDTO> setActive(@PathVariable Long id,
            @Valid @RequestBody com.company.integration_catalog.dto.ActiveStatusRequest request) {
        return ResponseEntity.ok(sourceService.setSourceActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sourceService.deleteSource(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<SourceResponseDTO>> getAllSources(
            @RequestParam(required = false) Boolean active) {
        List<SourceResponseDTO> items = sourceService.getAllSources();
        return ResponseEntity.ok(active == null ? items
                : items.stream().filter(item -> item.isActive() == active).toList());
    }

    @PostMapping
    public ResponseEntity<SourceResponseDTO> createSource(@Valid @RequestBody SourceRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.createSource(request));
    }
}
