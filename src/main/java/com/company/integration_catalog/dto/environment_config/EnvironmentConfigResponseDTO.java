package com.company.integration_catalog.dto.environment_config;

import com.company.integration_catalog.model.enums.AuthType;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public class EnvironmentConfigResponseDTO {
        private Long id;
        private ConfigTarget configTarget;
        private EnvironmentType environmentType;
        private String url;
        private Integer timeoutMs;
        private AuthType authType;
        private Map<String, Object> authCredentials;
        private Integer retryCount;
        private Integer retryIntervalMs;
    }