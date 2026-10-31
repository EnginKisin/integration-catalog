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
import com.company.integration_catalog.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
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
                .orElseThrow(() -> new ResourceNotFoundException("INTEGRATION_NOT_FOUND", "Integration not found with id: " + integrationId));

        MappingSchema replacement = PayloadSchemas.prepare(integration, request, file);
        boolean middleware = Boolean.TRUE.equals(integration.getHasMiddleware());
        Set<Long> obsoleteSchemaIds = integration.getMappingSchemas().stream()
                .filter(existing -> !existing.getSchemaType().isCompatibleWithFlow(middleware))
                .map(MappingSchema::getId)
                .collect(Collectors.toSet());
        MappingSchema schema = schemaRepository.findByIntegrationIdAndSchemaType(integrationId, request.getSchemaType())
                .orElse(null);
        if (schema != null) {
            if (!middleware && !PayloadSchemas.hasIndependentTarget(replacement) && PayloadSchemas.hasIndependentTarget(schema)
                    && PayloadSchemas.sideFormat(schema, true) == PayloadSchemas.expectedTargetFormat(integration, request.getSchemaType())) {
                replacement.setTargetSchemaDefinition(schema.getTargetSchemaDefinition());
                replacement.setTargetSamplePayload(schema.getTargetSamplePayload());
                replacement.setTargetFormat(schema.getTargetFormat());
                replacement.setTargetRawContent(schema.getTargetRawContent());
            }
            if (request.getSamplePayload() == null && Objects.equals(schema.getSchemaDefinition(), replacement.getSchemaDefinition())) {
                replacement.setSamplePayload(schema.getSamplePayload());
            }
            boolean hasMappings = false;
            for (FieldMapping mapping : integration.getFieldMappings()) {
                if (referencesSchema(mapping, obsoleteSchemaIds)) continue;
                boolean source = Objects.equals(mapping.getSourceSchema().getId(), schema.getId());
                boolean target = Objects.equals(mapping.getTargetSchema().getId(), schema.getId());
                if (!source && !target) continue;
                hasMappings = true;
                try {
                    if (source) PayloadSchemas.validateMappingPath(replacement, mapping.getSourceFieldPath());
                    if (target) PayloadSchemas.validateMappingPath(replacement, mapping.getTargetFieldPath(),
                            !middleware && source);
                } catch (PayloadValidationException ex) {
                    throw new PayloadValidationException(409, "SCHEMA_MAPPING_CONFLICT",
                            "Content change invalidates existing mappings; remove affected mappings first");
                }
            }
            if (hasMappings && (!Objects.equals(schema.getSchemaDefinition(), replacement.getSchemaDefinition())
                    || !Objects.equals(schema.getSamplePayload(), replacement.getSamplePayload())
                    || !Objects.equals(schema.getTargetSchemaDefinition(), replacement.getTargetSchemaDefinition())
                    || !Objects.equals(schema.getTargetSamplePayload(), replacement.getTargetSamplePayload())
                    || !Objects.equals(schema.getTargetFormat(), replacement.getTargetFormat())
                    || !Objects.equals(schema.getTargetRawContent(), replacement.getTargetRawContent())
                    || PayloadSchemas.format(schema) != replacement.getFormat())) integration.setReviewRequired(true);
            schema.setFormat(replacement.getFormat());
            schema.setSchemaDefinition(replacement.getSchemaDefinition());
            schema.setSamplePayload(replacement.getSamplePayload());
            schema.setTargetSchemaDefinition(replacement.getTargetSchemaDefinition());
            schema.setTargetSamplePayload(replacement.getTargetSamplePayload());
            schema.setTargetFormat(replacement.getTargetFormat());
            schema.setTargetRawContent(replacement.getTargetRawContent());
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
        if (!integration.getMappingSchemas().contains(savedSchema)) {
            integration.getMappingSchemas().add(savedSchema);
        }
        if (!obsoleteSchemaIds.isEmpty()) {
            boolean removedMappings = integration.getFieldMappings()
                    .removeIf(mapping -> referencesSchema(mapping, obsoleteSchemaIds));
            if (removedMappings) {
                fieldMappingRepository.flush();
            }
            integration.getMappingSchemas().removeIf(existing -> obsoleteSchemaIds.contains(existing.getId()));
        }
        integration.setStatus(IntegrationStatus.DRAFT);
        return mapper.toSchemaResponse(savedSchema);
    }

    private boolean referencesSchema(FieldMapping mapping, Set<Long> schemaIds) {
        return schemaIds.contains(mapping.getSourceSchema().getId())
                || schemaIds.contains(mapping.getTargetSchema().getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MappingSchemaResponseDTO> getSchemasByIntegration(Long integrationId) {
        if (!integrationRepository.existsById(integrationId)) {
            throw new ResourceNotFoundException("INTEGRATION_NOT_FOUND", "Integration not found with id: " + integrationId);
        }
        return schemaRepository.findByIntegrationId(integrationId).stream()
                .map(mapper::toSchemaResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SampleFile getSampleFile(Long integrationId, Long schemaId) {
        MappingSchema schema = schemaRepository.findByIdAndIntegrationId(schemaId, integrationId)
                .orElseThrow(() -> new ResourceNotFoundException("SCHEMA_NOT_FOUND", "Schema not found in integration: " + integrationId));
        if (schema.getSampleFile() == null) throw new ResourceNotFoundException("SAMPLE_FILE_NOT_FOUND", "No sample file for schema: " + schemaId);
        return new SampleFile(schema.getFileName(), schema.getContentType(), schema.getSampleFile());
    }

    @Override
    @Transactional
    public void saveFieldMappings(Long integrationId, List<FieldMappingRequestDTO> mappingRequests) {
        Integration integration = integrationRepository.findByIdForUpdate(integrationId)
                .orElseThrow(() -> new ResourceNotFoundException("INTEGRATION_NOT_FOUND", "Integration not found with id: " + integrationId));

        List<FieldMapping> newMappings = mappingRequests.stream().map(req -> {
            MappingSchema sourceSchema = schemaRepository.findByIdAndIntegrationId(req.getSourceSchemaId(), integrationId)
                    .orElseThrow(() -> new ResourceNotFoundException("SCHEMA_NOT_FOUND", "Source schema not found in integration: " + integrationId));

            MappingSchema targetSchema = schemaRepository.findByIdAndIntegrationId(req.getTargetSchemaId(), integrationId)
                    .orElseThrow(() -> new ResourceNotFoundException("SCHEMA_NOT_FOUND", "Target schema not found in integration: " + integrationId));

            PayloadSchemas.validateMappingPath(sourceSchema, req.getSourceFieldPath());
            PayloadSchemas.validateMappingPath(targetSchema, req.getTargetFieldPath(),
                    !Boolean.TRUE.equals(integration.getHasMiddleware()) && Objects.equals(sourceSchema.getId(), targetSchema.getId()));

            req.setIsAiGenerated(false);
            req.setAiConfidenceScore(null);

            FieldMapping replacement = mapper.toMappingEntity(req, integration, sourceSchema, targetSchema);
            replacement.setOrigin("MANUAL");
            integration.getFieldMappings().stream().filter(old ->
                    Objects.equals(old.getSourceSchema().getId(), sourceSchema.getId())
                    && Objects.equals(old.getTargetSchema().getId(), targetSchema.getId())
                    && Objects.equals(old.getSourceFieldPath(), req.getSourceFieldPath())
                    && Objects.equals(old.getTargetFieldPath(), req.getTargetFieldPath())).findFirst().ifPresent(old -> {
                        replacement.setOrigin(old.getOrigin());
                        replacement.setIsAiGenerated(old.getIsAiGenerated());
                        replacement.setAiConfidenceScore(old.getAiConfidenceScore());
                        replacement.setAiJobId(old.getAiJobId());
                        replacement.setAiSuggestionId(old.getAiSuggestionId());
                    });
            return replacement;
        }).collect(Collectors.toList());

        fieldMappingRepository.deleteByIntegrationId(integrationId);
        fieldMappingRepository.saveAll(newMappings);
        integration.setStatus(IntegrationStatus.DRAFT);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FieldMappingResponseDTO> getFieldMappingsByIntegration(Long integrationId) {
        return fieldMappingRepository.findByIntegrationId(integrationId).stream()
                .map(mapper::toMappingResponse)
                .collect(Collectors.toList());
    }
}
