package com.company.integration_catalog.service;

import com.company.integration_catalog.dto.FieldMappingDTO;
import com.company.integration_catalog.dto.MappingSchemaDTO;

import java.util.List;

public interface MappingService {

    // 4 büyük payload alanına şema kaydetmek/güncellemek için
    MappingSchemaDTO.Response saveSchema(Long integrationId, MappingSchemaDTO.Request request);

    // Entegrasyona ait yüklenmiş tüm şemaları listelemek için
    List<MappingSchemaDTO.Response> getSchemasByIntegration(Long integrationId);

    // Kullanıcının çektiği tüm çizgileri (Manuel mapping) topluca kaydetmek için
    void saveFieldMappings(Long integrationId, List<FieldMappingDTO.Request> mappingRequests);

    // Ekran açıldığında mevcut çizgileri frontend'e (React Flow) çizdirmek için
    List<FieldMappingDTO.Response> getFieldMappingsByIntegration(Long integrationId);
}