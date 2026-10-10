package com.company.integration_catalog.dto.mapping_schema;

import com.company.integration_catalog.model.enums.SchemaType;

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

        @NotNull (message = "Schema definition cannot be null")
        private Map<String, Object> schemaDefinition;
    }
