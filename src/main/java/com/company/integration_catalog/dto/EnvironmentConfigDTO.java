package com.company.integration_catalog.dto;

import com.company.integration_catalog.model.enums.AuthType;
import com.company.integration_catalog.model.enums.ConfigTarget;
import com.company.integration_catalog.model.enums.EnvironmentType;
import lombok.*;

import java.util.Map;


public final class EnvironmentConfigDTO {

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
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

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private ConfigTarget configTarget;
        private EnvironmentType environmentType;
        private String url;
        private Integer timeoutMs;
        private AuthType authType;
        private Map<String, Object> authCredentials; // Dinamik auth alanları Map olarak gelir
        private Integer retryCount;
        private Integer retryIntervalMs;
    }
}