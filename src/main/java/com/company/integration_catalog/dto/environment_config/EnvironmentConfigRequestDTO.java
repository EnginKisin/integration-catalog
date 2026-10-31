package com.company.integration_catalog.dto.environment_config;

import com.company.integration_catalog.model.enums.AuthType;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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

        @NotBlank(message = "URL cannot be blank")
        @Size(max = 100, message = "URL cannot exceed 100 characters")
        private String url;

        @Positive(message = "Timeout must be greater than zero")
        private Integer timeoutMs;

        @NotNull(message = "Auth type cannot be null")
        private AuthType authType;

        private Map<String, Object> authCredentials;
        @PositiveOrZero(message = "Retry count cannot be negative")
        private Integer retryCount;
        @PositiveOrZero(message = "Retry interval cannot be negative")
        private Integer retryIntervalMs;
    }

