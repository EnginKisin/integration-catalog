package com.company.integration_catalog.dto.field_mapping;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public class FieldMappingResponseDTO {
        private Long id;
        private Long sourceSchemaId;
        private String sourceFieldPath;
        private Long targetSchemaId;
        private String targetFieldPath;
        private Boolean isAiGenerated;
        private Double aiConfidenceScore;
    }