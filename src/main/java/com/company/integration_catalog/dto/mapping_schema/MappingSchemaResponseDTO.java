package com.company.integration_catalog.dto.mapping_schema;

import com.company.integration_catalog.model.enums.SchemaType;
import com.company.integration_catalog.model.enums.PayloadFormat;
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
        private Map<String, Object> samplePayload;
        private Map<String, Object> targetSchemaDefinition;
        private Map<String, Object> targetSamplePayload;
        private PayloadFormat targetFormat;
        private String targetRawContent;
        private PayloadFormat format;
        private String rawContent;
        private boolean mappingSupported;
        private CsvOptionsDTO csvOptions;
        private String fileName;
        private String contentType;
        private Long fileSize;
        private String sampleDownloadUrl;
    }
