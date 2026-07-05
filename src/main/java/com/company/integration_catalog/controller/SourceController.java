package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.LookUpDTO;
import com.company.integration_catalog.service.SourceService;
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
    public ResponseEntity<List<LookUpDTO.Response>> getAllSources() {
        return ResponseEntity.ok(sourceService.getAllSources());
    }

    @PostMapping
    public ResponseEntity<LookUpDTO.Response> createSource(@RequestBody LookUpDTO.Request request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.createSource(request));
    }
}