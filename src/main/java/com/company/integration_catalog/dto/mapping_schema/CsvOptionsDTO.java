package com.company.integration_catalog.dto.mapping_schema;

import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CsvOptionsDTO {
    private String delimiter;
    private Boolean hasHeader;
}
