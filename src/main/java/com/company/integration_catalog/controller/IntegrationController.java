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

    // 1. Dashboard: Tüm entegrasyonların listelenmesi ve ana arama çubuğu sorgusu
    @GetMapping
    public ResponseEntity<List<IntegrationResponseDTO>> getAllIntegrations(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(integrationService.getAllIntegrations(keyword));
    }

    // Entegrasyon Detayını Getir
    @GetMapping("/{id}")
    public ResponseEntity<IntegrationResponseDTO> getIntegrationById(@PathVariable Long id) {
        return ResponseEntity.ok(integrationService.getIntegrationById(id));
    }

    // 2. Create Wizard (Adım 1): Ana entegrasyon bilgilerini kaydetme
    @PostMapping
    public ResponseEntity<IntegrationResponseDTO> createIntegration(@Valid @RequestBody IntegrationRequestDTO request) {

        System.out.println("name = " + request.getName());
        System.out.println("description = " + request.getDescription());
        System.out.println("description = " + request.getSourceId());
        System.out.println("description = " + request.getDestinationId());
        System.out.println("description = " + request.getMiddlewareId());
        System.out.println("sourcePayloadFormat = " + request.getSourcePayloadFormat());
        System.out.println("type = " + request.getIsSync());
        System.out.println("destinationPayloadFormat = " + request.getDestinationPayloadFormat());
        System.out.println("hasMiddleware = " + request.getHasMiddleware());

        IntegrationResponseDTO created = integrationService.createIntegration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Entegrasyon Güncelleme (Gerekirse Adım 1'e geri dönüp düzenlemek için)
    @PutMapping("/{id}")
    public ResponseEntity<IntegrationResponseDTO> updateIntegration(
            @PathVariable Long id,
            @Valid @RequestBody IntegrationRequestDTO request) {
        return ResponseEntity.ok(integrationService.updateIntegration(id, request));
    }

    // 3. Create Wizard (Adım 2): Ortam konfigürasyonlarını topluca kaydetme
    @PostMapping("/{id}/configs")
    public ResponseEntity<Void> saveEnvironmentConfigs(
            @PathVariable Long id,
            @NotNull @Valid @RequestBody List<@NotNull @Valid EnvironmentConfigRequestDTO> configRequests) {
        integrationService.saveEnvironmentConfigs(id, configRequests);
        return ResponseEntity.ok().build();
    }

    // Entegrasyon Silme
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteIntegration(@PathVariable Long id) {
        integrationService.deleteIntegration(id);
        return ResponseEntity.noContent().build();
    }

    // IntegrationController.java içerisine eklenecek:
    @PatchMapping("/{id}/complete")
    public ResponseEntity<Void> completeIntegration(@PathVariable Long id) {
        integrationService.completeIntegration(id);
        return ResponseEntity.ok().build();
    }
}
