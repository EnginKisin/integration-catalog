package com.company.integration_catalog.dto;

import lombok.*;


public final class FieldMappingDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private Long sourceSchemaId;
        private String sourceFieldPath;
        private Long targetSchemaId;
        private String targetFieldPath;
        private Boolean isAiGenerated;
        private Double aiConfidenceScore;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private Long sourceSchemaId;
        private String sourceFieldPath;
        private Long targetSchemaId;
        private String targetFieldPath;
        private Boolean isAiGenerated;
        private Double aiConfidenceScore;
    }
}