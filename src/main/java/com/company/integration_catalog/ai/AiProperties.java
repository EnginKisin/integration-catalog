package com.company.integration_catalog.ai;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated @ConfigurationProperties("catalog.ai")
public class AiProperties {

    private boolean enabled;
    @NotBlank private String provider;
    @NotBlank private String baseUrl;
    @NotBlank @Size(max = 150) private String model;
    private String geminiApiKey;
    @Min(1) @Max(600) private int requestTimeoutSeconds;
    @Min(1) @Max(500) private int maxFields;
    @Min(1024) @Max(262144) private int maxInputBytes;
    @Min(2048) @Max(32768) private int contextTokens;
    @Min(256) @Max(8192) private int maxOutputTokens;
    @Min(1) @Max(100) private int maxPendingJobs;
    @Min(1) @Max(168) private int jobTtlHours;
}
