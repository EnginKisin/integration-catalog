package com.company.integration_catalog.dto.environment_config;

import com.company.integration_catalog.model.enums.AuthType;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
    public class EnvironmentConfigRequestDTO {
        @NotNull(message = "Config target cannot be null")
        private ConfigTarget configTarget;

        @NotNull(message = "Environment type cannot be null")
        private EnvironmentType environmentType;

        @NotNull(message = "URL cannot be null")
        private String url;

        private Integer timeoutMs;

        @NotNull(message = "Auth type cannot be null")
        private AuthType authType;

        private Map<String, Object> authCredentials; // Dinamik auth alanları Map olarak gelir
        private Integer retryCount;
        private Integer retryIntervalMs;
    }


