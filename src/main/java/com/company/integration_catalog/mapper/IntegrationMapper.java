package com.company.integration_catalog.mapper;

import com.company.integration_catalog.dto.environment_config.EnvironmentConfigRequestDTO;
import com.company.integration_catalog.dto.environment_config.EnvironmentConfigResponseDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingRequestDTO;
import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaRequestDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;
import com.company.integration_catalog.dto.integration.IntegrationRequestDTO;
import com.company.integration_catalog.dto.integration.IntegrationResponseDTO;
import com.company.integration_catalog.model.entity.Destination;
import com.company.integration_catalog.model.entity.EnvironmentConfig;
import com.company.integration_catalog.model.entity.FieldMapping;
import com.company.integration_catalog.model.entity.Integration;
import com.company.integration_catalog.model.entity.MappingSchema;
import com.company.integration_catalog.model.entity.Middleware;
import com.company.integration_catalog.model.entity.Source;
import com.company.integration_catalog.model.enums.IntegrationStatus;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class IntegrationMapper {

    // --- Integration Dönüşümleri ---

    public Integration toEntity(IntegrationRequestDTO request) {
        if (request == null) return null;

        return Integration.builder()
                .name(request.getName())
                .description(request.getDescription())
                // ID'leri doğrudan Hibernate proxy/referansı olarak setliyoruz (ilişki kurmak için yeterlidir)
                .source(request.getSourceId() != null ? Source.builder().id(request.getSourceId()).build() : null)
                .destination(request.getDestinationId() != null ? Destination.builder().id(request.getDestinationId()).build() : null)
                .middleware(request.getMiddlewareId() != null ? Middleware.builder().id(request.getMiddlewareId()).build() : null)
                .sourcePayloadFormat(request.getSourcePayloadFormat())
                .destinationPayloadFormat(request.getDestinationPayloadFormat())
                .isSync(request.getIsSync())
                .isSync(request.getIsSync() != null ? request.getIsSync() : false)
                .hasMiddleware(request.getHasMiddleware() != null ? request.getHasMiddleware() : false)
                .status(IntegrationStatus.DRAFT)
                .build();
    }

    public IntegrationResponseDTO toResponse(Integration entity) {
        if (entity == null) return null;

        List<EnvironmentConfigResponseDTO> configs = new ArrayList<>();
        if (entity.getEnvironmentConfigs() != null) {
            configs = entity.getEnvironmentConfigs().stream()
                    .map(this::toConfigResponse)
                    .collect(Collectors.toList());
        }

        return IntegrationResponseDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                // İlişkili tablolardan güvenli bir şekilde id ve name çekiyoruz
                .sourceId(entity.getSource() != null ? entity.getSource().getId() : null)
                .destinationId(entity.getDestination() != null ? entity.getDestination().getId() : null)
                .middlewareId(entity.getMiddleware() != null ? entity.getMiddleware().getId() : null)
                .sourcePayloadFormat(entity.getSourcePayloadFormat())
                .destinationPayloadFormat(entity.getDestinationPayloadFormat())
                .isSync(entity.getIsSync())
                .hasMiddleware(entity.getHasMiddleware())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .environmentConfigs(configs)
                .build();
    }


    // --- EnvironmentConfig Dönüşümleri ---

    public EnvironmentConfig toConfigEntity(EnvironmentConfigRequestDTO request, Integration integration) {
        if (request == null) return null;

        return EnvironmentConfig.builder()
                .integration(integration)
                .configTarget(request.getConfigTarget())
                .environmentType(request.getEnvironmentType())
                .url(request.getUrl())
                .timeoutMs(request.getTimeoutMs())
                .authType(request.getAuthType())
                .authCredentials(request.getAuthCredentials())
                .retryCount(request.getRetryCount())
                .retryIntervalMs(request.getRetryIntervalMs())
                .build();
    }

    public EnvironmentConfigResponseDTO toConfigResponse(EnvironmentConfig entity) {
        if (entity == null) return null;

        return EnvironmentConfigResponseDTO.builder()
                .id(entity.getId())
                .configTarget(entity.getConfigTarget())
                .environmentType(entity.getEnvironmentType())
                .url(entity.getUrl())
                .timeoutMs(entity.getTimeoutMs())
                .authType(entity.getAuthType())
                .authCredentials(entity.getAuthCredentials())
                .retryCount(entity.getRetryCount())
                .retryIntervalMs(entity.getRetryIntervalMs())
                .build();
    }

    // ... IntegrationMapper.java içerisine eklenecek yeni metotlar:

    // --- MappingSchema Dönüşümleri ---

    public MappingSchema toSchemaEntity(MappingSchemaRequestDTO request, Integration integration) {
        if (request == null) return null;

        return MappingSchema.builder()
                .integration(integration)
                .schemaType(request.getSchemaType())
                .schemaDefinition(request.getSchemaDefinition())
                .build();
    }

    public MappingSchemaResponseDTO toSchemaResponse(MappingSchema entity) {
        if (entity == null) return null;

        return MappingSchemaResponseDTO.builder()
                .id(entity.getId())
                .schemaType(entity.getSchemaType())
                .schemaDefinition(entity.getSchemaDefinition())
                .build();
    }

    // --- FieldMapping (Çizgiler) Dönüşümleri ---

    public FieldMapping toMappingEntity(FieldMappingRequestDTO request, 
                                        Integration integration, 
                                        MappingSchema sourceSchema, 
                                        MappingSchema targetSchema) {
        if (request == null) return null;

        return FieldMapping.builder()
                .integration(integration)
                .sourceSchema(sourceSchema)
                .sourceFieldPath(request.getSourceFieldPath())
                .targetSchema(targetSchema)
                .targetFieldPath(request.getTargetFieldPath())
                .isAiGenerated(request.getIsAiGenerated() != null ? request.getIsAiGenerated() : false)
                .aiConfidenceScore(request.getAiConfidenceScore())
                .build();
    }

    public FieldMappingResponseDTO toMappingResponse(FieldMapping entity) {
        if (entity == null) return null;

        return FieldMappingResponseDTO.builder()
                .id(entity.getId())
                .sourceSchemaId(entity.getSourceSchema().getId())
                .sourceFieldPath(entity.getSourceFieldPath())
                .targetSchemaId(entity.getTargetSchema().getId())
                .targetFieldPath(entity.getTargetFieldPath())
                .isAiGenerated(entity.getIsAiGenerated())
                .aiConfidenceScore(entity.getAiConfidenceScore())
                .build();
    }
}