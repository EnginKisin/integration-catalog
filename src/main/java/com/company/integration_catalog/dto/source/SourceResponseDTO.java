package com.company.integration_catalog.dto.source;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceResponseDTO {
    
    private Long id;
    private String name;
    private String description;
}
