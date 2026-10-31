package com.company.integration_catalog.ai;

import com.company.integration_catalog.dto.field_mapping.FieldMappingResponseDTO;
import com.company.integration_catalog.dto.mapping_schema.MappingSchemaResponseDTO;
import com.company.integration_catalog.payload.MappingField;
import com.company.integration_catalog.model.enums.PayloadFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class AiMappingDtos {
    private AiMappingDtos() {}
    public record Connection(@NotNull @Positive Long sourceSchemaId, @NotBlank @Size(max=500) String sourceFieldPath,
            @NotNull @Positive Long targetSchemaId, @NotBlank @Size(max=500) String targetFieldPath) {}
    public record CreateJob(@NotNull @Positive Long sourceSchemaId, @NotNull @Positive Long targetSchemaId,
            @NotBlank @Size(max=64) String expectedContextRevision,
            @Size(max=500) List<@NotNull @Valid Connection> protectedMappings,
            @Valid Options options) {
        public CreateJob { protectedMappings = protectedMappings == null ? List.of() : List.copyOf(protectedMappings); }
    }
    public record Options(boolean useSampleValues) {}
    public record AppendMapping(@Size(max=40) String suggestionId, @NotNull @Positive Long sourceSchemaId,
            @NotBlank @Size(max=500) String sourceFieldPath, @NotNull @Positive Long targetSchemaId,
            @NotBlank @Size(max=500) String targetFieldPath) {}
    public record AppendRequest(@NotBlank @Size(max=64) String expectedContextRevision,
            @Size(max=36) String aiJobId, @NotEmpty @Size(max=500) List<@NotNull @Valid AppendMapping> mappings) {}
    public record AppendResponse(String contextRevision, List<FieldMappingResponseDTO> addedMappings) {}
    public record Context(String contextRevision, List<MappingSchemaResponseDTO> schemas,
            List<FieldMappingResponseDTO> mappings) {}
    public record Capabilities(boolean enabled, boolean available, String provider, String model,
            List<String> supportedFormats, int maxFieldsPerSide, String code) {}
    public record JobView(String jobId, String status, Instant createdAt, Instant expiresAt,
            String errorCode, String message, String resultUrl, Integer pollAfterMs) {}
    public record Input(Long sourceSchemaId, Long targetSchemaId, String contextRevision,
            List<MappingField> sourceFields, List<MappingField> targetFields,
            List<Connection> protectedMappings, PayloadFormat sourceFormat, PayloadFormat targetFormat) {
        public Input {
            sourceFormat = sourceFormat == null ? PayloadFormat.JSON : sourceFormat;
            targetFormat = targetFormat == null ? PayloadFormat.JSON : targetFormat;
        }
        public Input(Long sourceSchemaId, Long targetSchemaId, String contextRevision,
                List<MappingField> sourceFields, List<MappingField> targetFields, List<Connection> protectedMappings) {
            this(sourceSchemaId, targetSchemaId, contextRevision, sourceFields, targetFields, protectedMappings,
                    PayloadFormat.JSON, PayloadFormat.JSON);
        }
    }
    public enum Confidence { HIGH, MEDIUM }
    public record Suggestion(String suggestionId, Long sourceSchemaId, String sourceFieldPath,
            Long targetSchemaId, String targetFieldPath, Confidence confidence, String reason) {}
    public record UnmatchedField(String fieldPath, String code, String reason) {}
    public record Suggestions(List<Suggestion> suggestions, List<UnmatchedField> unmatchedSourceFields,
            List<UnmatchedField> unmatchedTargetFields, List<String> warnings) {}
    public record Result(String jobId, String contextRevision, boolean stale, String provider, String model,
            List<Suggestion> suggestions, List<UnmatchedField> unmatchedSourceFields,
            List<UnmatchedField> unmatchedTargetFields, List<String> warnings) {}
}
