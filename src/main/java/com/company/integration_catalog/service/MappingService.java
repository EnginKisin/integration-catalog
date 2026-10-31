package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface MappingService {

    MappingSchemaResponseDTO saveSchema(Long integrationId, MappingSchemaRequestDTO request);

    MappingSchemaResponseDTO uploadSchema(Long integrationId, MappingSchemaRequestDTO metadata, MultipartFile file);

    SampleFile getSampleFile(Long integrationId, Long schemaId);

    record SampleFile(String fileName, String contentType, byte[] bytes) {}

    List<MappingSchemaResponseDTO> getSchemasByIntegration(Long integrationId);

    void saveFieldMappings(Long integrationId, List<FieldMappingRequestDTO> mappingRequests);

    List<FieldMappingResponseDTO> getFieldMappingsByIntegration(Long integrationId);
}
