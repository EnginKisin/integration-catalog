package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.destination.DestinationRequestDTO;
import com.company.integration_catalog.dto.destination.DestinationResponseDTO;
import com.company.integration_catalog.service.DestinationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
public class DestinationController {

    private final DestinationService destinationService;

    @GetMapping("/{id}")
    public ResponseEntity<DestinationResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(destinationService.getDestinationById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DestinationResponseDTO> update(@PathVariable Long id,
            @Valid @RequestBody DestinationRequestDTO request) {
        return ResponseEntity.ok(destinationService.updateDestination(id, request));
    }

    @PatchMapping("/{id}/active")
    public ResponseEntity<DestinationResponseDTO> setActive(@PathVariable Long id,
            @Valid @RequestBody com.company.integration_catalog.dto.ActiveStatusRequest request) {
        return ResponseEntity.ok(destinationService.setDestinationActive(id, request.active()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        destinationService.deleteDestination(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<DestinationResponseDTO>> getAllDestinations(
            @RequestParam(required = false) Boolean active) {
        List<DestinationResponseDTO> items = destinationService.getAllDestinations();
        return ResponseEntity.ok(active == null ? items
                : items.stream().filter(item -> item.isActive() == active).toList());
    }

    @PostMapping
    public ResponseEntity<DestinationResponseDTO> createDestination(@Valid @RequestBody DestinationRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(destinationService.createDestination(request));
    }
}
