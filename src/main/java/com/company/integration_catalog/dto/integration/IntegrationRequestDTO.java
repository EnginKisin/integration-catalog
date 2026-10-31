package com.company.integration_catalog.dto.integration;
import com.company.integration_catalog.model.enums.PayloadFormat;
import com.company.integration_catalog.validation.ValidMiddlewareSelection;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidMiddlewareSelection
    public class IntegrationRequestDTO {
        @NotBlank(message = "Integration name cannot be blank")
        @Size(max = 100, message = "Integration name cannot exceed 100 characters")
        private String name;

        @NotNull(message = "Integration description cannot be null")
        @Size(max = 500, message = "Integration description cannot exceed 500 characters")
        private String description;

        @NotNull(message = "Source system ID cannot be null")
        @Positive(message = "Source system ID must be greater than zero")
        private Long sourceSystemId;

        @NotNull(message = "Destination system ID cannot be null")
        @Positive(message = "Destination system ID must be greater than zero")
        private Long destinationSystemId;

        @Positive(message = "Middleware system ID must be greater than zero")
        private Long middlewareSystemId;

        @NotNull(message = "Source payload format cannot be null")
        private PayloadFormat sourcePayloadFormat;

        @NotNull(message = "Is sync cannot be null")
        private Boolean isSync;

        @NotNull(message = "Destination payload format cannot be null")
        private PayloadFormat destinationPayloadFormat;

        @NotNull(message = "Has middleware cannot be null")
        private Boolean hasMiddleware;
    }
