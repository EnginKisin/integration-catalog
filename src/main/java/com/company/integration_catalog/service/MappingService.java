package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface MappingService {

    // 4 büyük payload alanına şema kaydetmek/güncellemek için
    MappingSchemaResponseDTO saveSchema(Long integrationId, MappingSchemaRequestDTO request);

    MappingSchemaResponseDTO uploadSchema(Long integrationId, MappingSchemaRequestDTO metadata, MultipartFile file);

    SampleFile getSampleFile(Long integrationId, Long schemaId);

    record SampleFile(String fileName, String contentType, byte[] bytes) {}

    // Entegrasyona ait yüklenmiş tüm şemaları listelemek için
    List<MappingSchemaResponseDTO> getSchemasByIntegration(Long integrationId);

    // Kullanıcının çektiği tüm çizgileri (Manuel mapping) topluca kaydetmek için
    void saveFieldMappings(Long integrationId, List<FieldMappingRequestDTO> mappingRequests);

    // Ekran açıldığında mevcut çizgileri frontend'e (React Flow) çizdirmek için
    List<FieldMappingResponseDTO> getFieldMappingsByIntegration(Long integrationId);
}
