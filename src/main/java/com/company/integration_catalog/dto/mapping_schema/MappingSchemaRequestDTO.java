package com.company.integration_catalog.dto.mapping_schema;

import com.company.integration_catalog.model.enums.SchemaType;
import com.company.integration_catalog.model.enums.PayloadFormat;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public class MappingSchemaRequestDTO {

        @NotNull (message = "Schema type cannot be null")
        private SchemaType schemaType;

        private Map<String, Object> schemaDefinition;

        private PayloadFormat format;
        private String rawContent;
        private CsvOptionsDTO csvOptions;
    }
