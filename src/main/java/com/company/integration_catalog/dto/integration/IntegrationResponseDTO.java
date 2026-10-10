package com.company.integration_catalog.dto.integration;
import com.company.integration_catalog.model.enums.IntegrationStatus;
import com.company.integration_catalog.model.enums.PayloadFormat;
import com.company.integration_catalog.dto.environment_config.EnvironmentConfigResponseDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public class IntegrationResponseDTO {
        private Long id;
        private String name;
        private String description;
        private Long sourceId;
        private Long destinationId;
        private Long middlewareId;
        private PayloadFormat sourcePayloadFormat;
        private PayloadFormat destinationPayloadFormat;
        private Boolean isSync;
        private Boolean hasMiddleware;
        private IntegrationStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<EnvironmentConfigResponseDTO> environmentConfigs;
    }