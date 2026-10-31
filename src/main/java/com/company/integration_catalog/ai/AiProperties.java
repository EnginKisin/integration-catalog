package com.company.integration_catalog.ai;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated @ConfigurationProperties("catalog.ai")
public class AiProperties {
    private boolean enabled = false;
    @NotBlank private String provider = "ollama";
    @NotBlank private String baseUrl = "http://ollama:11434";
    @NotBlank @Size(max = 150) private String model = "qwen3.5:4b";
    private String geminiApiKey = "";
    @Min(1) @Max(600) private int requestTimeoutSeconds = 300;
    @Min(1) @Max(500) private int maxFields = 100;
    @Min(1024) @Max(262144) private int maxInputBytes = 65536;
    @Min(2048) @Max(32768) private int contextTokens = 8192;
    @Min(256) @Max(8192) private int maxOutputTokens = 2048;
    @Min(1) @Max(100) private int maxPendingJobs = 10;
    @Min(1) @Max(168) private int jobTtlHours = 24;
}
