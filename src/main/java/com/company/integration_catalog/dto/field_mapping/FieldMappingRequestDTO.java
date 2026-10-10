package com.company.integration_catalog.dto.field_mapping;

import jakarta.validation.constraints.NotNull;
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
    public class FieldMappingRequestDTO {
        @NotNull(message = "Source schema ID cannot be null")
        private Long sourceSchemaId;

        @NotNull(message = "Source field path cannot be null")
        private String sourceFieldPath;

        @NotNull(message = "Target schema ID cannot be null")
        private Long targetSchemaId;

        @NotNull(message = "Target field path cannot be null")
        private String targetFieldPath;

        @NotNull (message = "isAiGenerated cannot be null")
        private Boolean isAiGenerated;
        
        private Double aiConfidenceScore;
    }

