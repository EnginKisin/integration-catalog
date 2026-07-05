package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.EnvironmentConfigDTO;
import com.company.integration_catalog.dto.IntegrationDTO;
import com.company.integration_catalog.service.IntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Geliştirme aşamasında frontend'in (CORS) takılmaması için
public class IntegrationController {

    private final IntegrationService integrationService;

    // 1. Dashboard: Tüm entegrasyonların listelenmesi ve ana arama çubuğu sorgusu
    @GetMapping
    public ResponseEntity<List<IntegrationDTO.Response>> getAllIntegrations(
            @RequestParam(value = "keyword", required = false) String keyword) {
        return ResponseEntity.ok(integrationService.getAllIntegrations(keyword));
    }

    // Entegrasyon Detayını Getir
    @GetMapping("/{id}")
    public ResponseEntity<IntegrationDTO.Response> getIntegrationById(@PathVariable Long id) {
        return ResponseEntity.ok(integrationService.getIntegrationById(id));
    }

    // 2. Create Wizard (Adım 1): Ana entegrasyon bilgilerini kaydetme
    @PostMapping
    public ResponseEntity<IntegrationDTO.Response> createIntegration(@RequestBody IntegrationDTO.Request request) {
        IntegrationDTO.Response created = integrationService.createIntegration(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Entegrasyon Güncelleme (Gerekirse Adım 1'e geri dönüp düzenlemek için)
    @PutMapping("/{id}")
    public ResponseEntity<IntegrationDTO.Response> updateIntegration(
            @PathVariable Long id, 
            @RequestBody IntegrationDTO.Request request) {
        return ResponseEntity.ok(integrationService.updateIntegration(id, request));
    }

    // 3. Create Wizard (Adım 2): Ortam konfigürasyonlarını topluca kaydetme
    @PostMapping("/{id}/configs")
    public ResponseEntity<Void> saveEnvironmentConfigs(
            @PathVariable Long id, 
            @RequestBody List<EnvironmentConfigDTO.Request> configRequests) {
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