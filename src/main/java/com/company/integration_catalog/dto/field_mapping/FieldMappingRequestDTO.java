package com.company.integration_catalog.dto.field_mapping;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

        @NotBlank(message = "Source field path cannot be blank")
        @Size(max = 500, message = "Source field path cannot exceed 500 characters")
        private String sourceFieldPath;

        @NotNull(message = "Target schema ID cannot be null")
        private Long targetSchemaId;

        @NotBlank(message = "Target field path cannot be blank")
        @Size(max = 500, message = "Target field path cannot exceed 500 characters")
        private String targetFieldPath;

        @NotNull (message = "isAiGenerated cannot be null")
        private Boolean isAiGenerated;
        
        private Double aiConfidenceScore;
    }

