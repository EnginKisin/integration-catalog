package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationResponseDTO;
import com.company.integration_catalog.service.IntegrationService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
public class IntegrationController {

    private final IntegrationService integrationService;

    @PostMapping("/{id}/review/confirm")
    public ResponseEntity<IntegrationResponseDTO> confirmReview(@PathVariable Long id,
            @Valid @RequestBody ReviewConfirmationRequest request) {
        return ResponseEntity.ok(integrationService.confirmIntegrationReview(id));
    }

    public record ReviewConfirmationRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.AssertTrue Boolean confirmed) {}

    @GetMapping
    public ResponseEntity<List<IntegrationResponseDTO>> getAllIntegrations(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(integrationService.getAllIntegrations(keyword));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IntegrationResponseDTO> getIntegrationById(@PathVariable Long id) {
        return ResponseEntity.ok(integrationService.getIntegrationById(id));
    }

    @PostMapping
    public ResponseEntity<IntegrationResponseDTO> createIntegration(@Valid @RequestBody IntegrationRequestDTO request) {


        IntegrationResponseDTO created = integrationService.createIntegration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IntegrationResponseDTO> updateIntegration(
            @PathVariable Long id,
            @Valid @RequestBody IntegrationRequestDTO request) {
        return ResponseEntity.ok(integrationService.updateIntegration(id, request));
    }

    @PostMapping("/{id}/configs")
    public ResponseEntity<Void> saveEnvironmentConfigs(
            @PathVariable Long id,
            @NotNull @Valid @RequestBody List<@NotNull @Valid EnvironmentConfigRequestDTO> configRequests) {
        integrationService.saveEnvironmentConfigs(id, configRequests);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable Long id) {
        integrationService.deleteIntegration(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<Void> completeIntegration(@PathVariable Long id) {
        integrationService.completeIntegration(id);
        return ResponseEntity.ok().build();
    }
}
