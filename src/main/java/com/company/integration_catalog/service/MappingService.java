package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;

import java.util.List;

public interface MappingService {

    // 4 büyük payload alanına şema kaydetmek/güncellemek için
    MappingSchemaResponseDTO saveSchema(Long integrationId, MappingSchemaRequestDTO request);

    // Entegrasyona ait yüklenmiş tüm şemaları listelemek için
    List<MappingSchemaResponseDTO> getSchemasByIntegration(Long integrationId);

    // Kullanıcının çektiği tüm çizgileri (Manuel mapping) topluca kaydetmek için
    void saveFieldMappings(Long integrationId, List<FieldMappingRequestDTO> mappingRequests);

    // Ekran açıldığında mevcut çizgileri frontend'e (React Flow) çizdirmek için
    List<FieldMappingResponseDTO> getFieldMappingsByIntegration(Long integrationId);
}