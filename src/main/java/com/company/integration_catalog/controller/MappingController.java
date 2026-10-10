package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;
import com.company.integration_catalog.service.MappingService;

import jakarta.validation.Valid;
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
    public ResponseEntity<MappingSchemaResponseDTO> saveSchema(
            @Valid 
            @PathVariable Long integrationId, 
            @RequestBody MappingSchemaRequestDTO request) {
        return ResponseEntity.ok(mappingService.saveSchema(integrationId, request));
    }

    // Bir entegrasyona ait yüklenmiş tüm şemaları (ağaç yapılarını) frontend için listeleme
    @GetMapping("/schemas")
    public ResponseEntity<List<MappingSchemaResponseDTO>> getSchemas(@PathVariable Long integrationId) {
        return ResponseEntity.ok(mappingService.getSchemasByIntegration(integrationId));
    }

    // 2. Visual Mapping (Adım 3): Çizilen tüm çizgileri (Manuel mapping) topluca kaydetme
    @PostMapping("/fields")
    public ResponseEntity<Void> saveFieldMappings(
            @Valid
            @PathVariable Long integrationId, 
            @RequestBody List<FieldMappingRequestDTO> mappingRequests) {
        mappingService.saveFieldMappings(integrationId, mappingRequests);
        return ResponseEntity.ok().build();
    }

    // React Flow ekranı açıldığında veritabanındaki çizgileri çekip ön yüze çizdirme
    @GetMapping("/fields")
    public ResponseEntity<List<FieldMappingResponseDTO>> getFieldMappings(@PathVariable Long integrationId) {
        return ResponseEntity.ok(mappingService.getFieldMappingsByIntegration(integrationId));
    }
}