package com.company.integration_catalog.dto.middleware;

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
public class MiddlewareResponseDTO {
    
    private Long id;
    private String name;
    private String description;
}
