package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.FieldMappingDTO;
import com.company.integration_catalog.dto.MappingSchemaDTO;
import com.company.integration_catalog.service.MappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integrations/{integrationId}/mappings")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MappingController {

    private final MappingService mappingService;

    // 1. Visual Mapping (Adım 3): 4 büyük payload alanından birine şema yükleme / güncelleme
    @PostMapping("/schemas")
    public ResponseEntity<MappingSchemaDTO.Response> saveSchema(
            @PathVariable Long integrationId, 
            @RequestBody MappingSchemaDTO.Request request) {
        return ResponseEntity.ok(mappingService.saveSchema(integrationId, request));
    }

    // Bir entegrasyona ait yüklenmiş tüm şemaları (ağaç yapılarını) frontend için listeleme
    @GetMapping("/schemas")
    public ResponseEntity<List<MappingSchemaDTO.Response>> getSchemas(@PathVariable Long integrationId) {
        return ResponseEntity.ok(mappingService.getSchemasByIntegration(integrationId));
    }

    // 2. Visual Mapping (Adım 3): Çizilen tüm çizgileri (Manuel mapping) topluca kaydetme
    @PostMapping("/fields")
    public ResponseEntity<Void> saveFieldMappings(
            @PathVariable Long integrationId, 
            @RequestBody List<FieldMappingDTO.Request> mappingRequests) {
        mappingService.saveFieldMappings(integrationId, mappingRequests);
        return ResponseEntity.ok().build();
    }

    // React Flow ekranı açıldığında veritabanındaki çizgileri çekip ön yüze çizdirme
    @GetMapping("/fields")
    public ResponseEntity<List<FieldMappingDTO.Response>> getFieldMappings(@PathVariable Long integrationId) {
        return ResponseEntity.ok(mappingService.getFieldMappingsByIntegration(integrationId));
    }
}