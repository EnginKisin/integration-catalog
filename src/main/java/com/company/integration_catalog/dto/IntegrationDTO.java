package com.company.integration_catalog.dto;

import com.company.integration_catalog.model.enums.PayloadFormat;
import com.company.integration_catalog.model.enums.IntegrationStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;


public final class IntegrationDTO {

    // --- FRONTEND'E VERİ DÖNERKEN KULLANILACAK (RESPONSE) ---
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response {
        private Long id;
        private String name;
        private String description;
        private Long sourceId;
        private String sourceName;
        private Long destinationId;
        private String destinationName;
        private Long middlewareId;
        private String middlewareName;
        private PayloadFormat sourcePayloadFormat;
        private PayloadFormat destinationPayloadFormat;
        private Boolean hasMiddleware;
        private IntegrationStatus status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<EnvironmentConfigDTO.Response> environmentConfigs;
    }

    // --- YENİ KAYIT VE GÜNCELLEME İSTEKLERİ İÇİN (REQUEST) ---

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request {
        private String name;
        private String description;
        // Frontend selectbox'tan seçilen ID'leri gönderecek:
        private Long sourceId;
        private Long destinationId;
        private Long middlewareId; // Eğer hasMiddleware true ise dolu gelecek
        private PayloadFormat sourcePayloadFormat;
        private PayloadFormat destinationPayloadFormat;
        private Boolean hasMiddleware;
    }
}
