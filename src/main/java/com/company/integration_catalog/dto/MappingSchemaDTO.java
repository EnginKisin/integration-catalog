package com.company.integration_catalog.dto;

import com.company.integration_catalog.model.enums.SchemaType;
import lombok.*;
import java.util.Map;


public final class MappingSchemaDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private SchemaType schemaType;
        private Map<String, Object> schemaDefinition;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private SchemaType schemaType;
        private Map<String, Object> schemaDefinition;
    }
}