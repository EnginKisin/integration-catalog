package com.company.integration_catalog.service.impl;

import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;
import com.company.integration_catalog.mapper.IntegrationMapper;
import com.company.integration_catalog.model.entity.FieldMapping;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.model.enums.IntegrationStatus;
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
import java.util.Objects;
import org.springframework.web.multipart.MultipartFile;
import com.company.integration_catalog.payload.PayloadSchemas;
import com.company.integration_catalog.exception.PayloadValidationException;

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
        return storeSchema(integrationId, request, null);
    }

    @Override
    @Transactional
    public MappingSchemaResponseDTO uploadSchema(Long integrationId, MappingSchemaRequestDTO metadata, MultipartFile file) {
        return storeSchema(integrationId, metadata, file);
    }

    private MappingSchemaResponseDTO storeSchema(Long integrationId, MappingSchemaRequestDTO request, MultipartFile file) {
        Integration integration = integrationRepository.findByIdForUpdate(integrationId)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + integrationId));

        MappingSchema replacement = PayloadSchemas.prepare(integration, request, file);
        MappingSchema schema = schemaRepository.findByIntegrationIdAndSchemaType(integrationId, request.getSchemaType())
                .orElse(null);
        if (schema != null) {
            // Validate every affected mapping before changing managed state; never silently discard links.
            boolean hasMappings = false;
            for (FieldMapping mapping : fieldMappingRepository.findByIntegrationId(integrationId)) {
                boolean source = Objects.equals(mapping.getSourceSchema().getId(), schema.getId());
                boolean target = Objects.equals(mapping.getTargetSchema().getId(), schema.getId());
                if (!source && !target) continue;
                hasMappings = true;
                try {
                    if (source) PayloadSchemas.validateMappingPath(replacement, mapping.getSourceFieldPath());
                    if (target) PayloadSchemas.validateMappingPath(replacement, mapping.getTargetFieldPath());
                } catch (PayloadValidationException ex) {
                    throw new PayloadValidationException(409, "SCHEMA_MAPPING_CONFLICT",
                            "Content change invalidates existing mappings; remove affected mappings first");
                }
            }
            if (hasMappings && (!Objects.equals(schema.getSchemaDefinition(), replacement.getSchemaDefinition())
                    || PayloadSchemas.format(schema) != replacement.getFormat())) integration.setReviewRequired(true);
            schema.setFormat(replacement.getFormat());
            schema.setSchemaDefinition(replacement.getSchemaDefinition());
            schema.setRawContent(replacement.getRawContent());
            schema.setSampleFile(replacement.getSampleFile());
            schema.setFileName(replacement.getFileName());
            schema.setContentType(replacement.getContentType());
            schema.setCsvDelimiter(replacement.getCsvDelimiter());
            schema.setCsvHasHeader(replacement.getCsvHasHeader());
        } else {
            schema = replacement;
        }

        MappingSchema savedSchema = schemaRepository.save(schema);
        integration.setStatus(IntegrationStatus.DRAFT);
        return mapper.toSchemaResponse(savedSchema);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MappingSchemaResponseDTO> getSchemasByIntegration(Long integrationId) {
        if (!integrationRepository.existsById(integrationId)) {
            throw new EntityNotFoundException("Integration not found with id: " + integrationId);
        }
        return schemaRepository.findByIntegrationId(integrationId).stream()
                .map(mapper::toSchemaResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SampleFile getSampleFile(Long integrationId, Long schemaId) {
        MappingSchema schema = schemaRepository.findByIdAndIntegrationId(schemaId, integrationId)
                .orElseThrow(() -> new EntityNotFoundException("Schema not found in integration: " + integrationId));
        if (schema.getSampleFile() == null) throw new EntityNotFoundException("No sample file for schema: " + schemaId);
        return new SampleFile(schema.getFileName(), schema.getContentType(), schema.getSampleFile());
    }

    @Override
    @Transactional
    public void saveFieldMappings(Long integrationId, List<FieldMappingRequestDTO> mappingRequests) {
        Integration integration = integrationRepository.findByIdForUpdate(integrationId)
                .orElseThrow(() -> new EntityNotFoundException("Integration not found with id: " + integrationId));

        // Tüm şemaların bu entegrasyona ait olduğunu silme işleminden önce doğrula.
        List<FieldMapping> newMappings = mappingRequests.stream().map(req -> {
            MappingSchema sourceSchema = schemaRepository.findByIdAndIntegrationId(req.getSourceSchemaId(), integrationId)
                    .orElseThrow(() -> new EntityNotFoundException("Source schema not found in integration: " + integrationId));

            MappingSchema targetSchema = schemaRepository.findByIdAndIntegrationId(req.getTargetSchemaId(), integrationId)
                    .orElseThrow(() -> new EntityNotFoundException("Target schema not found in integration: " + integrationId));

            PayloadSchemas.validateMappingPath(sourceSchema, req.getSourceFieldPath());
            PayloadSchemas.validateMappingPath(targetSchema, req.getTargetFieldPath());

            // v1 Manuel süreç olduğu için isAiGenerated'ı doğrudan false set edebiliriz
            req.setIsAiGenerated(false);
            req.setAiConfidenceScore(null);

            return mapper.toMappingEntity(req, integration, sourceSchema, targetSchema);
        }).collect(Collectors.toList());

        fieldMappingRepository.deleteByIntegrationId(integrationId);
        fieldMappingRepository.saveAll(newMappings);
        integration.setStatus(IntegrationStatus.DRAFT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FieldMappingResponseDTO> getFieldMappingsByIntegration(Long integrationId) {
        return fieldMappingRepository.findByIntegrationId(integrationId).stream()
                .map(mapper::toMappingResponse) // Mapper içindeki field mapping dönüşümünü çağırır
                .collect(Collectors.toList());
    }
}
