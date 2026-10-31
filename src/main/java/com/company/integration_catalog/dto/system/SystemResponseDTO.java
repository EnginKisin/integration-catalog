package com.company.integration_catalog.dto.system;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private boolean active;
    private long usageCount;
}
