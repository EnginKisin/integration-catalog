package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.FieldMapping;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.repository.FieldMappingRepository;
import com.company.integration_catalog.repository.IntegrationRepository;
import com.company.integration_catalog.repository.MappingSchemaRepository;
import com.company.integration_catalog.service.MappingService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MappingServiceImpl implements MappingService {

    private final IntegrationRepository integrationRepository;
    private final MappingSchemaRepository schemaRepository;
    private final FieldMappingRepository fieldMappingRepository;
    private final IntegrationMapper mapper;

    @Override
    @Transactional
    public MappingSchemaResponseDTO saveSchema(Long integrationId, MappingSchemaRequestDTO request) {
        Integration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + integrationId));

        // Eğer bu şema tipi daha önce yüklenmişse, üzerine yazıp güncelliyoruz
        MappingSchema schema = schemaRepository.findByIntegrationIdAndSchemaType(integrationId, request.getSchemaType())
                .map(existingSchema -> {
                    existingSchema.setSchemaDefinition(request.getSchemaDefinition());
                    return existingSchema;
                })
                .orElseGet(() -> mapper.toSchemaEntity(request, integration));

        MappingSchema savedSchema = schemaRepository.save(schema);
        return mapper.toSchemaResponse(savedSchema);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MappingSchemaResponseDTO> getSchemasByIntegration(Long integrationId) {
        return schemaRepository.findByIntegrationId(integrationId).stream()
                .map(mapper::toSchemaResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveFieldMappings(Long integrationId, List<FieldMappingRequestDTO> mappingRequests) {
        Integration integration = integrationRepository.findById(integrationId)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + integrationId));

        // 1. O entegrasyona ait eski manuel çizgileri temizle
        fieldMappingRepository.deleteByIntegrationId(integrationId);

        // 2. Yeni gelen çizgileri doğrula ve kaydet
        List<FieldMapping> newMappings = mappingRequests.stream().map(req -> {
            MappingSchema sourceSchema = schemaRepository.findById(req.getSourceSchemaId())
                    .orElseThrow(() -> new EntityNotFoundException("Source schema not found with id: " + req.getSourceSchemaId()));

            MappingSchema targetSchema = schemaRepository.findById(req.getTargetSchemaId())
                    .orElseThrow(() -> new EntityNotFoundException("Target schema not found with id: " + req.getTargetSchemaId()));

            // v1 Manuel süreç olduğu için isAiGenerated'ı doğrudan false set edebiliriz
            req.setIsAiGenerated(false);
            req.setAiConfidenceScore(null);

            return mapper.toMappingEntity(req, integration, sourceSchema, targetSchema);
        }).collect(Collectors.toList());

        fieldMappingRepository.saveAll(newMappings);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FieldMappingResponseDTO> getFieldMappingsByIntegration(Long integrationId) {
        return fieldMappingRepository.findByIntegrationId(integrationId).stream()
                .map(mapper::toMappingResponse) // Mapper içindeki field mapping dönüşümünü çağırır
                .collect(Collectors.toList());
    }
}