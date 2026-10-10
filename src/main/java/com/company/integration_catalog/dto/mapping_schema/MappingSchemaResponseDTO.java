package com.company.integration_catalog.dto.mapping_schema;

import com.company.integration_catalog.model.enums.SchemaType;
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
    public class MappingSchemaResponseDTO {
        private Long id;
        private SchemaType schemaType;
        private Map<String, Object> schemaDefinition;
    }