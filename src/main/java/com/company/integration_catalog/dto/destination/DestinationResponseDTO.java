package com.company.integration_catalog.dto.destination;

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
public class DestinationResponseDTO {
    
    private Long id;
    private String name;
    private String description;
    private boolean active;
    private long usageCount;
}
