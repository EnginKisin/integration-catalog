package com.company.integration_catalog.controller;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;
import com.company.integration_catalog.service.MappingService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/integrations/{integrationId}/mappings")
@RequiredArgsConstructor
public class MappingController {

    private final MappingService mappingService;

    @PostMapping("/schemas")
    public ResponseEntity<MappingSchemaResponseDTO> saveSchema(
            @PathVariable Long integrationId,
            @Valid @RequestBody MappingSchemaRequestDTO request) {
        return ResponseEntity.ok(mappingService.saveSchema(integrationId, request));
    }

    @GetMapping("/schemas")
    public ResponseEntity<List<MappingSchemaResponseDTO>> getSchemas(@PathVariable Long integrationId) {
        return ResponseEntity.ok(mappingService.getSchemasByIntegration(integrationId));
    }

    @PostMapping(value = "/schemas/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MappingSchemaResponseDTO> uploadSchema(
            @PathVariable Long integrationId,
            @Valid @RequestPart("metadata") MappingSchemaRequestDTO metadata,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(mappingService.uploadSchema(integrationId, metadata, file));
    }

    @GetMapping("/schemas/{schemaId}/sample")
    public ResponseEntity<byte[]> downloadSample(@PathVariable Long integrationId, @PathVariable Long schemaId) {
        MappingService.SampleFile sample = mappingService.getSampleFile(integrationId, schemaId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(sample.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(sample.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentLength(sample.bytes().length).body(sample.bytes());
    }

    @PostMapping("/fields")
    public ResponseEntity<Void> saveFieldMappings(
            @PathVariable Long integrationId,
            @NotNull @Valid @RequestBody List<@NotNull @Valid FieldMappingRequestDTO> mappingRequests) {
        mappingService.saveFieldMappings(integrationId, mappingRequests);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/fields")
    public ResponseEntity<List<FieldMappingResponseDTO>> getFieldMappings(@PathVariable Long integrationId) {
        return ResponseEntity.ok(mappingService.getFieldMappingsByIntegration(integrationId));
    }
}
