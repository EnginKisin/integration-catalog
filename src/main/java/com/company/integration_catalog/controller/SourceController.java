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
@CrossOrigin(origins = "*")
public class SourceController {

    private final SourceService sourceService;

    @GetMapping
    public ResponseEntity<List<SourceResponseDTO>> getAllSources() {
        return ResponseEntity.ok(sourceService.getAllSources());
    }

    @PostMapping
    public ResponseEntity<SourceResponseDTO> createSource(@Valid @RequestBody SourceRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.createSource(request));
    }
}